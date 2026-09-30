package pt.aguiarvieira.xmuks.core.data.media

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Caches gomuks media under what it is, `mxc://server/id` (and which thumbnail, if one), not under
 * the URL it was fetched by: the same image through another server address, with a push's
 * `image_auth` token, or with gomuks' `fallback=` letter is one entry in memory and on disk.
 */
class MxcCacheKeys : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val key = (request.data as? String)?.let(::mxcCacheKey) ?: return chain.proceed()
        return chain
            .withRequest(
                request
                    .newBuilder()
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .build(),
            ).proceed()
    }
}

/**
 * The cache key for a gomuks media URL (`…/_gomuks/media/{server}/{id}?…`): its `mxc://` URI, with
 * `#thumbnail=<size>` for a thumbnail. Null for anything else (our own files, other hosts' images).
 */
fun mxcCacheKey(url: String): String? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    val segments = parsed.pathSegments
    val at = segments.indexOf("_gomuks")
    val valid = at >= 0 && segments.size == at + MEDIA_PATH && segments[at + 1] == "media"
    if (!valid) return null
    val mxc = "mxc://${segments[at + 2]}/${segments[at + 3]}"
    return parsed.queryParameter("thumbnail")?.let { "$mxc#thumbnail=$it" } ?: mxc
}

/** `_gomuks`, `media`, server, id. */
private const val MEDIA_PATH = 4
