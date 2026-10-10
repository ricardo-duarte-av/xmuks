package pt.aguiarvieira.xmuks.core.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls

class MediaUrlsTest {
    private val urls = MediaUrls { "https://gomuks.example.org/prefix/".toHttpUrl() }

    @Test
    fun `avatar thumbnails go through gomuks' media endpoint`() =
        assertEquals(
            "https://gomuks.example.org/prefix/_gomuks/media/matrix.org/AbC123?thumbnail=avatar",
            urls.avatar("mxc://matrix.org/AbC123"),
        )

    @Test
    fun `malformed or missing mxc URIs give no URL`() {
        listOf(null, "", "https://x/y", "mxc://", "mxc://server", "mxc://server/", "mxc:///id", "mxc://a/b/c").forEach {
            assertNull(it, urls.avatar(it))
        }
    }

    @Test
    fun `sender fallback is the localpart`() =
        assertEquals(
            "alice",
            pt.aguiarvieira.xmuks.core.data.rooms
                .localpart("@alice:example.org")
        )

    @Test
    fun `encrypted files carry their keys when there are any`() {
        val keys =
            pt.aguiarvieira.xmuks.core.data.timeline
                .FileKeys("k-_", "iv+/", "h=")
        assertEquals(
            "https://gomuks.example.org/prefix/_gomuks/media/s/id?encrypted=true&crypto_version=v2" +
                "&crypto_key=k-_&crypto_iv=iv%2B%2F&crypto_hash=h%3D",
            urls.media("mxc://s/id", encrypted = true, keys = keys),
        )
        assertEquals("https://gomuks.example.org/prefix/_gomuks/media/s/id?encrypted=true", urls.media("mxc://s/id", true))
    }

    @Test
    fun `no server, no URL`() = assertNull(MediaUrls { null }.avatar("mxc://a/b"))
}
