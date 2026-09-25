package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class Connecting(
        val attempt: Int,
    ) : ConnectionState

    /** Receiving the initial (or catch-up) sync; [rooms] counts rooms applied so far. */
    data class Initializing(
        val rooms: Int,
        val catchup: Boolean,
    ) : ConnectionState

    data object Live : ConnectionState

    data class Retrying(
        val attempt: Int,
        val inMs: Long,
        val error: String,
    ) : ConnectionState

    /** The stored password was rejected; nothing more happens until the user logs in again. */
    data object AuthFailed : ConnectionState
}

/** Persists where the stream is up to. [save] is called only after the frame's data is applied. */
interface ResumeStore {
    suspend fun load(): ResumePoint

    suspend fun save(point: ResumePoint)
}

/**
 * Keeps one `/sse` stream open while [run] is active: reconnects with capped, jittered backoff,
 * resumes from where it left off, and acknowledges applied events so gomuks can free its buffer.
 *
 * Every frame goes through [sink] *before* the resume point advances past it, so a crash or
 * disconnect mid-apply re-delivers the frame rather than losing it.
 */
class GomuksConnection(
    private val sse: SseClient,
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val resumeStore: ResumeStore,
    private val io: CoroutineContext,
    private val sink: suspend (GomuksFrame) -> Unit,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Skip the current backoff wait, e.g. when the network comes back. */
    fun reconnectNow() {
        wake.trySend(Unit)
    }

    /** Runs until cancelled (typically: the app leaves the foreground). */
    suspend fun run() {
        var attempt = 0
        try {
            while (currentCoroutineContext().isActive) {
                _state.value = ConnectionState.Connecting(attempt)
                val error = runOnce(onLive = { attempt = 0 })
                if (error is HttpStatusException && error.code == HTTP_UNAUTHORIZED) {
                    _state.value = ConnectionState.AuthFailed
                    return
                }
                val wait = backoffMs(attempt++)
                _state.value = ConnectionState.Retrying(attempt, wait, error?.message ?: "Stream closed")
                // Wait out the backoff, unless reconnectNow() (network back) cuts it short.
                if (withTimeoutOrNull(wait) { wake.receive() } != null) attempt = 0
            }
        } finally {
            // AuthFailed must outlive the loop: it's what tells the UI to ask for the password again.
            if (_state.value != ConnectionState.AuthFailed) _state.value = ConnectionState.Idle
        }
    }

    /** One connection's lifetime. Returns why it ended (null: server closed it cleanly). */
    private suspend fun runOnce(onLive: () -> Unit): Throwable? {
        var point = resumeStore.load()
        return try {
            coroutineScope {
                val acker = launch { ackLoop { point } }
                try {
                    sse.open(point).collect { frame ->
                        point = beforeApply(frame, point)
                        track(frame)
                        sink(frame)
                        point = afterApply(frame, point)
                        if (frame.event == GomuksEvent.InitComplete) {
                            _state.value = ConnectionState.Live
                            onLive()
                        }
                    }
                } finally {
                    acker.cancel()
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            e
        } catch (e: SerializationException) {
            // A line we can't parse is a protocol mismatch, not a network blip; reconnecting
            // re-delivers it, so back off and surface it rather than spin.
            e
        }
    }

    /** A new backend run invalidates buffered request IDs; a resumed run keeps them. */
    private fun beforeApply(
        frame: GomuksFrame,
        point: ResumePoint,
    ): ResumePoint {
        val event = frame.event as? GomuksEvent.RunId ?: return point
        val sameRun = event.runId == point.runId
        return point.copy(
            runId = event.runId,
            listenerId = event.listenerId,
            lastRequestId = if (sameRun) point.lastRequestId else 0,
        )
    }

    /** Advances and persists the resume point once [frame] has been applied by the sink. */
    private suspend fun afterApply(
        frame: GomuksFrame,
        point: ResumePoint,
    ): ResumePoint {
        val serverTs = (frame.event as? GomuksEvent.Sync)?.sync?.serverTimestamp ?: 0
        val next =
            point.copy(
                lastRequestId = if (frame.requestId < 0) frame.requestId else point.lastRequestId,
                lastServerTs = maxOf(point.lastServerTs, serverTs),
            )
        if (next != point || frame.event is GomuksEvent.RunId) resumeStore.save(next)
        return next
    }

    /** Surfaces initial-sync progress (rooms received so far) until the stream is live. */
    private fun track(frame: GomuksFrame) {
        val sync = (frame.event as? GomuksEvent.Sync)?.sync ?: return
        val state = _state.value
        if (state is ConnectionState.Live) return
        val before = (state as? ConnectionState.Initializing)?.rooms ?: 0
        _state.value = ConnectionState.Initializing(before + sync.rooms.size, sync.catchup)
    }

    /** `POST /_gomuks/sse/ping`: tells gomuks which buffered events are applied here. */
    private suspend fun ackLoop(current: () -> ResumePoint) {
        var acked = 0L
        while (true) {
            delay(ACK_INTERVAL_MS)
            val point = current()
            if (point.lastRequestId != acked && ack(point)) acked = point.lastRequestId
        }
    }

    private suspend fun ack(point: ResumePoint): Boolean {
        val base = server()
        val runId = point.runId
        val ackable = base != null && runId != null && point.lastRequestId != 0L && point.listenerId != 0L
        if (!ackable) return false
        val url =
            base
                .gomuks("sse", "ping")
                .newBuilder()
                .addQueryParameter("run_id", runId)
                .addQueryParameter("listener_id", point.listenerId.toString())
                .addQueryParameter("last_received_event", point.lastRequestId.toString())
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody())
                .build()
        return withContext(io) {
            runCatching { http.newCall(request).await().use { it.isSuccessful } }.getOrDefault(false)
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val base = (INITIAL_BACKOFF_MS shl attempt.coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_MS)
        return base / 2 + Random.nextLong(base / 2 + 1)
    }

    private companion object {
        const val ACK_INTERVAL_MS = 10_000L
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
