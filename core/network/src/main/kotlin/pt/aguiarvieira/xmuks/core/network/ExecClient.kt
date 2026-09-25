package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.CoroutineContext

sealed interface ExecResult {
    data class Ok(
        val data: JsonElement,
    ) : ExecResult

    /** gomuks ran the command and it failed (HTTP 418), or rejected the request (4xx). */
    data class CommandError(
        val status: Int,
        val errcode: String?,
        val message: String,
    ) : ExecResult

    /** Never got an answer. For a [ExecMode.Write] the command may or may not have run. */
    data class NetworkError(
        val cause: IOException,
    ) : ExecResult
}

enum class ExecMode {
    /** Safe to repeat: retried on network errors as-is. */
    Read,

    /**
     * Has side effects: sent with a `txn_id` + `start_ts` envelope and retried under the *same*
     * envelope, so gomuks collapses a retry of a command that already ran (5-minute window) into
     * the first attempt's result instead of executing it twice.
     */
    Write,
}

/** `POST /_gomuks/exec/{command}`: the RPC command set over plain HTTP. */
class ExecClient(
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val io: CoroutineContext,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newTxnId: () -> String = { "xmuks-" + UUID.randomUUID() },
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    suspend fun exec(
        command: String,
        data: JsonElement = JsonObject(emptyMap()),
        mode: ExecMode,
    ): ExecResult {
        val base = server() ?: return ExecResult.NetworkError(IOException("Not logged in"))
        val url =
            base
                .gomuks("exec", command)
                .newBuilder()
                .apply {
                    if (mode == ExecMode.Write) {
                        addQueryParameter("txn_id", newTxnId())
                        addQueryParameter("start_ts", clock().toString())
                    }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .post(data.toString().toRequestBody(JSON))
                .build()
        var attempt = 0
        while (true) {
            val result = withContext(io) { once(request) }
            val retryable =
                result is ExecResult.NetworkError ||
                    (result is ExecResult.CommandError && result.status in RETRY_STATUS)
            if (!retryable || attempt >= retryDelaysMs.size) return result
            delay(retryDelaysMs[attempt++])
        }
    }

    private suspend fun once(request: Request): ExecResult =
        try {
            http.newCall(request).await().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) {
                    ExecResult.Ok(if (text.isBlank()) JsonNull else GomuksJson.parseToJsonElement(text))
                } else {
                    val obj = runCatching { GomuksJson.parseToJsonElement(text).jsonObject }.getOrNull()
                    ExecResult.CommandError(
                        status = response.code,
                        errcode = obj?.get("errcode")?.jsonPrimitive?.content,
                        message = obj?.get("error")?.jsonPrimitive?.content ?: text.take(ERROR_BODY_CHARS),
                    )
                }
            }
        } catch (e: IOException) {
            ExecResult.NetworkError(e)
        }

    private companion object {
        val JSON = "application/json".toMediaType()
        val DEFAULT_RETRY_DELAYS_MS = listOf(500L, 1_500L, 4_000L)

        /** Proxy/backend hiccups where the command did not run (or is de-duplicated by txn_id). */
        @Suppress("MagicNumber") // Bad Gateway, Service Unavailable, Gateway Timeout
        val RETRY_STATUS = setOf(502, 503, 504)
    }
}
