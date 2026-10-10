package pt.aguiarvieira.xmuks.core.data.media

import okhttp3.HttpUrl
import pt.aguiarvieira.xmuks.core.data.timeline.FileKeys
import pt.aguiarvieira.xmuks.core.data.timeline.Media

/**
 * Turns `mxc://server/id` into gomuks' `/_gomuks/media/{server}/{id}` URL. The request carries the
 * session cookie (same OkHttp client as everything else), so no token is embedded in the URL and
 * the URL — hence the image cache key — is stable across sessions.
 */
class MediaUrls(
    private val server: () -> HttpUrl?,
) {
    fun avatar(mxc: String?): String? = build(mxc) { addQueryParameter("thumbnail", "avatar") }

    fun full(mxc: String?): String? = build(mxc) {}

    /**
     * Timeline media; gomuks decrypts `encrypted` files itself with the keys it kept from the event,
     * or with [keys] when it kept none (it doesn't for MSC4274 gallery items).
     */
    fun media(
        mxc: String?,
        encrypted: Boolean,
        keys: FileKeys? = null,
    ): String? =
        build(mxc) {
            if (encrypted) addQueryParameter("encrypted", "true")
            if (encrypted && keys != null) {
                addQueryParameter("crypto_version", "v2")
                addQueryParameter("crypto_key", keys.key)
                addQueryParameter("crypto_iv", keys.iv)
                addQueryParameter("crypto_hash", keys.sha256)
            }
        }

    /** A message's file. */
    fun file(media: Media): String? = media(media.mxc, media.encrypted, media.keys)

    private fun build(
        mxc: String?,
        extra: HttpUrl.Builder.() -> Unit,
    ): String? {
        // Our own files (a thumbnail shown while its upload runs) load as they are.
        if (mxc != null && mxc.startsWith("file:")) return mxc
        val (host, id) = parseMxc(mxc) ?: return null
        val base = server() ?: return null
        return base
            .newBuilder()
            .addPathSegment("_gomuks")
            .addPathSegment("media")
            .addPathSegment(host)
            .addPathSegment(id)
            .apply(extra)
            .build()
            .toString()
    }

    companion object {
        /** `mxc://server/mediaId` → (server, mediaId), or null if malformed. */
        fun parseMxc(mxc: String?): Pair<String, String>? {
            if (mxc == null || !mxc.startsWith(MXC)) return null
            val rest = mxc.removePrefix(MXC)
            val slash = rest.indexOf('/')
            if (slash <= 0 || slash == rest.lastIndex) return null
            val id = rest.substring(slash + 1)
            return if ('/' in id) null else rest.substring(0, slash) to id
        }

        private const val MXC = "mxc://"
    }
}
