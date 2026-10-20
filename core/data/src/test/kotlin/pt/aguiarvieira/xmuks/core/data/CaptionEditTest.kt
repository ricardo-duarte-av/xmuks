package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.captionEdit
import pt.aguiarvieira.xmuks.core.data.timeline.messageParams

class CaptionEditTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.content

    @Test
    fun `an uncaptioned picture keeps its name as filename, for the caption to take the body`() {
        val (base, extra) = captionEdit(json("""{"msgtype":"m.image","body":"cat.png","url":"mxc://x/y","info":{"w":4}}"""))
        assertEquals("cat.png", base.str("body"))
        assertEquals("cat.png", base.str("filename"))
        assertEquals("mxc://x/y", base.str("url"))
        assertNull(extra.str("body"))
    }

    @Test
    fun `a captioned one drops the old caption, its HTML, mentions and relations`() {
        val (base, extra) =
            captionEdit(
                json(
                    """{"msgtype":"m.video","body":"old","format":"org.matrix.custom.html","formatted_body":"<b>old</b>",
                    "filename":"v.mp4","file":{"url":"mxc://x/v"},"m.mentions":{},"m.relates_to":{"m.in_reply_to":{}}}""",
                ),
            )
        assertEquals("v.mp4", base.str("body"))
        for (key in listOf("format", "formatted_body", "m.mentions", "m.relates_to")) {
            assertNull(key, base[key])
            assertNull(key, extra[key])
        }
        assertEquals("mxc://x/v", base["file"]!!.jsonObject.str("url"))
    }

    @Test
    fun `what gomuks doesn't know goes beside it - gallery items, spoiler marks`() {
        val (base, extra) =
            captionEdit(
                json(
                    """{"msgtype":"dm.filament.gallery","body":"trip","itemtypes":[{"itemtype":"m.image"}],
                    "page.codeberg.everypizza.msc4193.spoiler":true}""",
                ),
            )
        assertEquals("", base.str("body"))
        assertNull(base["filename"])
        assertEquals(1, (extra["itemtypes"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals("true", extra.str("page.codeberg.everypizza.msc4193.spoiler"))
    }

    @Test
    fun `a new file for our message goes as its edit - the new content, no reply`() {
        val file = json("""{"msgtype":"m.image","body":"new.png","url":"mxc://x/new"}""")
        val params =
            messageParams("!r", "caption", ReplyTarget("\$other", "@bob:x"), editing = "\$mine", baseContent = file)
        val relation = params["relates_to"]!!.jsonObject
        assertEquals("m.replace", relation.str("rel_type"))
        assertEquals("\$mine", relation.str("event_id"))
        assertNull(relation["m.in_reply_to"])
        assertEquals("mxc://x/new", params["base_content"]!!.jsonObject.str("url"))
    }
}
