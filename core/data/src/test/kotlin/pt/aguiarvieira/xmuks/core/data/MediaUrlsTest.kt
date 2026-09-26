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
    fun `no server, no URL`() = assertNull(MediaUrls { null }.avatar("mxc://a/b"))
}
