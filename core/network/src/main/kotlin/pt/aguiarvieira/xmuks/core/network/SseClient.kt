package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.aguiarvieira.xmuks.core.protocol.FrameDecoder
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/** Where to pick the stream up from. All-zero means "fresh initial sync". */
data class ResumePoint(
    /** Backend run the other fields belong to; resume only works within the same run. */
    val runId: String? = null,
    /** Most recent (most negative) buffered request ID applied locally. */
    val lastRequestId: Long = 0,
    val listenerId: Long = 0,
    /**
     * `server_timestamp` of the last fully applied sync. With a cache that is still intact, gomuks
     * sends only what changed since (a catch-up) instead of the full snapshot.
     */
    val lastServerTs: Long = 0,
)

class HttpStatusException(
    val code: Int,
    message: String,
) : IOException("HTTP $code: $message")

/**
 * One `GET /_gomuks/sse` connection in `application/jsonl` form: one JSON object per line, `null`
 * as the keepalive ping (every ~15 s). The flow completes when the server closes the stream and
 * throws on errors; reconnecting is [GomuksConnection]'s job. [http] should have a read timeout a
 * few pings long, so a silently dead connection fails instead of hanging.
 */
class SseClient(
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val io: CoroutineContext,
) {
    fun open(resume: ResumePoint): Flow<GomuksFrame> =
        channelFlow {
            val base = server() ?: throw IOException("Not logged in")
            val request =
                Request
                    .Builder()
                    .url(sseUrl(base, resume))
                    .header("Accept", "application/jsonl")
                    .build()
            val call = http.newCall(request)
            val reader =
                launch(io) {
                    try {
                        call.await().use { response ->
                            if (!response.isSuccessful) throw HttpStatusException(response.code, response.message)
                            val source = response.body.source()
                            while (true) {
                                val line = source.readUtf8Line() ?: break
                                FrameDecoder.decode(line)?.let { send(it) }
                            }
                        }
                    } catch (e: IOException) {
                        // The read we aborted ourselves on the way out is not an error.
                        if (!call.isCanceled()) throw e
                    }
                }
            try {
                reader.join()
            } finally {
                // The reader is parked in a blocking socket read that coroutine cancellation can't
                // interrupt, and closing the response would wait on that same read. Cancelling the
                // call fails the read immediately — so a stopped stream is torn down now, not at
                // the next ping or read timeout.
                call.cancel()
            }
        }

    private fun sseUrl(
        base: HttpUrl,
        resume: ResumePoint,
    ): HttpUrl =
        base
            .gomuks("sse")
            .newBuilder()
            .apply {
                resume.runId?.let { addQueryParameter("run_id", it) }
                resume.lastRequestId.nonZero()?.let { addQueryParameter("last_received_event", it) }
                resume.listenerId.nonZero()?.let { addQueryParameter("prev_listener_id", it) }
                resume.lastServerTs.nonZero()?.let { addQueryParameter("last_server_ts", it) }
            }.build()

    private fun Long.nonZero(): String? = takeIf { it != 0L }?.toString()
}
