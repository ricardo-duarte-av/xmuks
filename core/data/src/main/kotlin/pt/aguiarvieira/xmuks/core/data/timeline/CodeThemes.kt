package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.aguiarvieira.xmuks.core.network.await
import kotlin.coroutines.CoroutineContext

/**
 * Code block colour themes as gomuks serves them (`GET /_gomuks/codeblock/{name}.css`, Chroma's
 * CSS for the `code_block_theme` preference), kept once fetched.
 */
class CodeThemes(
    private val http: OkHttpClient,
    private val server: () -> HttpUrl?,
    private val io: CoroutineContext,
) {
    private val lock = Mutex()
    private val css = mutableMapOf<String, String>()

    /** The theme's CSS; null when gomuks doesn't have it or can't be reached (asked again next time). */
    suspend fun css(name: String): String? =
        lock.withLock {
            css[name] ?: fetch(name)?.also { css[name] = it }
        }

    private suspend fun fetch(name: String): String? {
        val url =
            server()
                ?.newBuilder()
                ?.addPathSegment("_gomuks")
                ?.addPathSegment("codeblock")
                ?.addPathSegment("$name.css")
                ?.build() ?: return null
        return withContext(io) {
            runCatching {
                http.newCall(Request.Builder().url(url).build()).await().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            }.getOrNull()
        }
    }
}
