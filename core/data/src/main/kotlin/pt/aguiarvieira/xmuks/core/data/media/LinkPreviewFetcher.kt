package pt.aguiarvieira.xmuks.core.data.media

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.aguiarvieira.xmuks.core.data.timeline.BundledPreview
import pt.aguiarvieira.xmuks.core.network.await
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * `GET /_gomuks/url_preview`: the homeserver's preview of a link, its image re-uploaded by gomuks
 * (encrypted for an encrypted room) so it can be bundled in a message.
 */
class LinkPreviewFetcher(
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val io: CoroutineContext,
) {
    suspend fun fetch(
        link: String,
        encrypt: Boolean,
    ): Result<BundledPreview> {
        val base = server() ?: return Result.failure(IOException("Not logged in"))
        val url =
            base
                .newBuilder()
                .addPathSegment("_gomuks")
                .addPathSegment("url_preview")
                .addQueryParameter("encrypt", encrypt.toString())
                .addQueryParameter("url", link)
                .build()
        return withContext(io) {
            runCatching {
                http.newCall(Request.Builder().url(url).build()).await().use { response ->
                    val json = GomuksJson.parseToJsonElement(response.body.string()) as? JsonObject
                    if (!response.isSuccessful || json == null) {
                        val error = (json?.get("error") as? JsonPrimitive)?.content
                        throw IOException(error ?: "HTTP ${response.code}")
                    }
                    BundledPreview.of(json)
                }
            }
        }
    }
}
