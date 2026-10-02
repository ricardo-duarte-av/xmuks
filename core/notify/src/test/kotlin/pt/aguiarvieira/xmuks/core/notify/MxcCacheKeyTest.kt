package pt.aguiarvieira.xmuks.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MxcCacheKeyTest {
    @Test
    fun sameImageSameKeyWhateverTheUrl() {
        val app = mxcCacheKey("https://g.example/prefix/_gomuks/media/matrix.org/AbC?thumbnail=avatar")
        val push = mxcCacheKey("https://other.host/_gomuks/media/matrix.org/AbC?encrypted=false&fallback=R&thumbnail=avatar&image_auth=t0k")
        assertEquals("mxc://matrix.org/AbC#thumbnail=avatar", app)
        assertEquals(app, push)
    }

    @Test
    fun fullFilesAndThumbnailsDiffer() = assertEquals("mxc://s/id", mxcCacheKey("https://g/_gomuks/media/s/id?encrypted=true"))

    @Test
    fun notGomuksMedia() {
        assertNull(mxcCacheKey("file:///data/x.jpg"))
        assertNull(mxcCacheKey("https://example.org/pic.png"))
    }
}
