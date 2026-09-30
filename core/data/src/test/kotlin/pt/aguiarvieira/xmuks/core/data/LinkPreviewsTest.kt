package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.linkPreviewsOf
import pt.aguiarvieira.xmuks.core.data.timeline.previewableLinks
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class LinkPreviewsTest {
    private fun json(s: String) = GomuksJson.parseToJsonElement(s) as JsonObject

    @Test
    fun readsBeeperPreviewsWithEncryptedImages() {
        val previews =
            linkPreviewsOf(
                json(
                    """{"body":"x","com.beeper.linkpreviews":[
                    {"matched_url":"https://xkcd.com/386","og:url":"https://xkcd.com/386/","og:title":"Duty Calls",
                     "og:image:width":601,"og:image:height":659,"beeper:image:encryption":{"url":"mxc://s/id"}},
                    {"matched_url":"https://example.org"}]}""",
                ),
            )
        val p = previews.single()
        assertEquals("https://xkcd.com/386/", p.url)
        assertEquals("Duty Calls", p.title)
        assertEquals("mxc://s/id", p.image?.mxc)
        assertTrue(p.image!!.encrypted)
    }

    @Test
    fun findsLinksButNotMatrixTo() =
        assertEquals(
            listOf("https://a.org/x?y=1", "http://b.net"),
            previewableLinks("see https://a.org/x?y=1 and http://b.net, https://matrix.to/#/@a:b"),
        )
}
