package pt.aguiarvieira.xmuks.core.data.outbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDao
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxEntity
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxState
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlin.math.min

/**
 * The durable outbox: everything the user sends is written here first, then handed to gomuks
 * over `/exec` — and it is never sent twice.
 *
 * gomuks collapses repeats of one `txn_id` while its `start_ts` is under ~2.5 minutes old, so:
 * - within [SAFE_WINDOW_MS] an entry is simply retried under the same envelope;
 * - past it, an entry no attempt can have reached gomuks with (the connection never opened) gets a
 *   fresh envelope and goes again;
 * - one that *may* have reached gomuks without an answer becomes [OutboxState.Unknown]: never
 *   re-sent automatically, the user decides.
 * Once gomuks accepts it the entry is gone and gomuks' own local echo (then the real event) takes
 * over; delivery to the homeserver is gomuks' job from there, with failures as the event's send_error.
 * Rooms are independent: one stuck room doesn't hold up another; within a room order is kept.
 */
class Outbox(
    private val dao: OutboxDao,
    private val transport: Transport,
    /** gomuks accepted a message: here's its local echo. */
    private val onAccepted: suspend (Event) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : AccountScoped {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val attempts = HashMap<String, Int>()
    private var loop: Job? = null

    /** Starts the sender (idempotent). Unsent entries from a previous run go out first. */
    fun start() {
        if (loop == null) loop = scope.launch { run() }
        poke()
    }

    /** Try now rather than at the next backoff tick (e.g. the connection came back). */
    fun poke() {
        wake.trySend(Unit)
    }

    fun observe(roomId: String): Flow<List<OutboxEntity>> = dao.observe(roomId)

    suspend fun sendMessage(
        roomId: String,
        params: JsonObject,
    ) = enqueue(roomId, SEND_MESSAGE, params)

    suspend fun enqueue(
        roomId: String,
        command: String,
        params: JsonObject,
    ): String {
        val now = clock()
        val entry =
            OutboxEntity(
                localId = newId(),
                roomId = roomId,
                createdAt = now,
                command = command,
                params = params.toString(),
                txnId = newTxnId(newId),
                startTs = now,
                state = OutboxState.Queued.name,
            )
        dao.insert(entry)
        poke()
        return entry.localId
    }

    /** The user chose to send a failed or maybe-sent entry again: a new, independent attempt. */
    suspend fun resend(localId: String) {
        val entry = dao.get(localId) ?: return
        val now = clock()
        dao.update(
            entry.copy(
                state = OutboxState.Queued.name,
                txnId = newTxnId(newId),
                startTs = now,
                maybeDelivered = false,
                error = null,
            ),
        )
        attempts.remove(localId)
        poke()
    }

    suspend fun discard(localId: String) {
        dao.delete(localId)
        attempts.remove(localId)
    }

    override suspend fun clearAccountData() {
        dao.wipe()
        attempts.clear()
    }

    // --- sending ------------------------------------------------------------------------------

    private suspend fun run() {
        while (true) {
            when (val wait = sendPass()) {
                NOTHING_QUEUED -> wake.receive()

                // Something resolved (sent, failed, unknown): look again at once.
                null -> Unit

                else -> withTimeoutOrNull(wait) { wake.receive() }
            }
        }
    }

    /**
     * One try at the oldest queued entry of each room (order within a room, independence between
     * rooms). Returns the shortest backoff to wait, null if something resolved, or
     * [NOTHING_QUEUED].
     */
    internal suspend fun sendPass(): Long? {
        val heads =
            dao
                .queued()
                .groupBy { it.roomId }
                .values
                .map { it.first() }
        if (heads.isEmpty()) return NOTHING_QUEUED
        var wait: Long? = null
        var resolved = false
        for (head in heads) {
            val next = attempt(head)
            if (next == null) resolved = true else wait = min(wait ?: next, next)
        }
        return if (resolved) null else wait
    }

    /** One try. Returns how long to wait before the next one, or null when the entry is resolved. */
    private suspend fun attempt(queued: OutboxEntity): Long? {
        var entry = queued
        val now = clock()
        if (now - entry.startTs > SAFE_WINDOW_MS) {
            if (entry.maybeDelivered) {
                dao.update(entry.copy(state = OutboxState.Unknown.name))
                return null
            }
            // Nothing ever reached gomuks: a fresh envelope is safe.
            entry = entry.copy(txnId = newTxnId(newId), startTs = now)
        }
        val before = entry.maybeDelivered
        // Record the risk before the request leaves: if the app dies mid-request, the entry is
        // treated as possibly sent.
        dao.update(entry.copy(maybeDelivered = true))
        val params = GomuksJson.parseToJsonElement(entry.params).jsonObject
        return when (val result = transport.execOnce(entry.command, params, entry.txnId, entry.startTs)) {
            is ExecResult.Ok -> {
                accepted(entry, result)
            }

            is ExecResult.CommandError -> {
                rejected(entry, result, before)
            }

            is ExecResult.NetworkError -> {
                val reached = !result.cause.neverReachedServer()
                dao.update(entry.copy(maybeDelivered = before || reached))
                backoff(entry)
            }
        }
    }

    private suspend fun accepted(
        entry: OutboxEntity,
        result: ExecResult.Ok,
    ): Long? {
        dao.delete(entry.localId)
        attempts.remove(entry.localId)
        runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), result.data) }
            .getOrNull()
            ?.let { onAccepted(it) }
        return null
    }

    private suspend fun rejected(
        entry: OutboxEntity,
        result: ExecResult.CommandError,
        before: Boolean,
    ): Long? =
        when {
            // gomuks refused the envelope before running anything.
            result.errcode == REQUEST_EXPIRED -> {
                if (before) {
                    dao.update(entry.copy(state = OutboxState.Unknown.name, maybeDelivered = true))
                    null
                } else {
                    dao.update(entry.copy(txnId = newTxnId(newId), startTs = clock(), maybeDelivered = false))
                    0
                }
            }

            result.errcode == TIME_DESYNC -> {
                fail(entry, "This device's clock is ahead of the server's")
            }

            // The proxy never got an answer from gomuks, or gomuks never got the request.
            result.status in UNREACHED_STATUS -> {
                dao.update(entry.copy(maybeDelivered = before))
                backoff(entry)
            }

            // The proxy gave up waiting: gomuks may be running it.
            result.status == GATEWAY_TIMEOUT -> {
                backoff(entry)
            }

            // gomuks ran the command and it failed: nothing was sent.
            else -> {
                fail(entry, result.message)
            }
        }

    private suspend fun fail(
        entry: OutboxEntity,
        message: String,
    ): Long? {
        dao.update(entry.copy(state = OutboxState.Failed.name, error = message, maybeDelivered = false))
        attempts.remove(entry.localId)
        return null
    }

    private fun backoff(entry: OutboxEntity): Long {
        val n = attempts.merge(entry.localId, 1, Int::plus) ?: 1
        return min(FIRST_RETRY_MS shl (n - 1).coerceAtMost(MAX_SHIFT), MAX_RETRY_MS)
    }

    /** How commands reach gomuks: one attempt under a caller-owned envelope ([ExecClient.execOnce]). */
    fun interface Transport {
        suspend fun execOnce(
            command: String,
            data: JsonElement,
            txnId: String,
            startTs: Long,
        ): ExecResult
    }

    companion object {
        const val SEND_MESSAGE = "send_message"
        internal const val NOTHING_QUEUED = -1L

        /** gomuks' limit is 2.5 minutes; margin for clock skew and transit. */
        const val SAFE_WINDOW_MS = 120_000L
        private const val FIRST_RETRY_MS = 1_000L
        private const val MAX_RETRY_MS = 30_000L
        private const val MAX_SHIFT = 5
        private const val REQUEST_EXPIRED = "FI.MAU.GOMUKS.REQUEST_EXPIRED"
        private const val TIME_DESYNC = "FI.MAU.GOMUKS.TIME_DESYNC"
        private const val GATEWAY_TIMEOUT = 504

        @Suppress("MagicNumber") // Bad Gateway, Service Unavailable
        private val UNREACHED_STATUS = setOf(502, 503)
    }
}

private fun newTxnId(newId: () -> String) = "xmuks-${newId()}"

/** The request provably never left this device / reached the server. */
private fun IOException.neverReachedServer() =
    this is ConnectException ||
        this is UnknownHostException ||
        this is NoRouteToHostException ||
        this is SSLHandshakeException ||
        message == NOT_LOGGED_IN

private const val NOT_LOGGED_IN = "Not logged in"
