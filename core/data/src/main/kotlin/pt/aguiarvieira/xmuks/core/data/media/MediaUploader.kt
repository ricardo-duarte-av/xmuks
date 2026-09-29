package pt.aguiarvieira.xmuks.core.data.media

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import pt.aguiarvieira.xmuks.core.network.await
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/** Bytes to upload: opened (possibly more than once, on a retry) when the request is written. */
class UploadSource(
    val length: Long,
    val open: () -> InputStream,
)

/**
 * `POST /_gomuks/upload`: gomuks takes the file, works out its metadata (size, dimensions,
 * duration, a blurhash for images, a waveform for voice), encrypts it when asked, uploads it to
 * the homeserver, and answers with ready-to-send message content (`url`, or `file` when encrypted).
 *
 * Progress covers both legs: our upload to gomuks (most of the bar: that's the phone's
 * connection), then gomuks' own to the homeserver, which it streams as fractions.
 */
class MediaUploader(
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val io: CoroutineContext,
) {
    suspend fun upload(
        source: UploadSource,
        filename: String,
        mimeType: String?,
        encrypt: Boolean,
        voiceMessage: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): Result<JsonObject> {
        val base = server() ?: return Result.failure(IOException("Not logged in"))
        val url =
            base
                .newBuilder()
                .addPathSegment("_gomuks")
                .addPathSegment("upload")
                .addQueryParameter("filename", filename)
                .addQueryParameter("encrypt", encrypt.toString())
                .addQueryParameter("progress", "true")
                .apply { if (voiceMessage) addQueryParameter("voice_message", "true") }
                .build()
        val body = CountingBody(source, mimeType?.toMediaTypeOrNull()) { onProgress(it * OUR_SHARE) }
        val request =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()
        return withContext(io) {
            runCatching {
                http.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(errorOf(response.body.string()) ?: "HTTP ${response.code}")
                    }
                    readProgressStream(response.body.source()) { onProgress(OUR_SHARE + it * (1 - OUR_SHARE)) }
                }
            }
        }
    }

    /** Lines of progress fractions, then the content (or an error object). */
    private fun readProgressStream(
        source: okio.BufferedSource,
        onProgress: (Float) -> Unit,
    ): JsonObject {
        while (true) {
            val line = source.readUtf8Line() ?: throw IOException("Upload ended without a result")
            if (line.isBlank()) continue
            when (val element = GomuksJson.parseToJsonElement(line)) {
                is JsonPrimitive -> {
                    element.contentOrNull?.toFloatOrNull()?.let(onProgress)
                }

                is JsonObject -> {
                    if (element.containsKey("errcode") && !element.containsKey("msgtype")) {
                        throw IOException(
                            (element["error"] as? JsonPrimitive)?.contentOrNull ?: element.toString(),
                        )
                    }
                    return element
                }

                else -> {}
            }
        }
    }

    private fun errorOf(body: String): String? {
        val obj = runCatching { GomuksJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        return (obj?.get("error") as? JsonPrimitive)?.contentOrNull
    }

    /** Streams [source] and reports how much of it is written. */
    private class CountingBody(
        private val source: UploadSource,
        private val type: MediaType?,
        private val onProgress: (Float) -> Unit,
    ) : RequestBody() {
        override fun contentType() = type

        override fun contentLength() = source.length

        override fun writeTo(sink: BufferedSink) {
            source.open().source().use { input ->
                var written = 0L
                while (true) {
                    val read = input.read(sink.buffer, CHUNK)
                    if (read == -1L) break
                    sink.emitCompleteSegments()
                    written += read
                    if (source.length > 0) onProgress((written.toFloat() / source.length).coerceAtMost(1f))
                }
            }
        }
    }

    private companion object {
        /** How much of the bar is our own upload; the rest is gomuks'. */
        const val OUR_SHARE = 0.85f
        const val CHUNK = 64L * 1024
    }
}
