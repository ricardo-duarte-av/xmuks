package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.SPOILER
import pt.aguiarvieira.xmuks.core.data.timeline.forwardContent

class ForwardContentTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.content

    @Test
    fun `the file goes again, named for itself, without the caption, reply or mentions`() {
        val (base, extra) =
            forwardContent(
                json(
                    """{"msgtype":"m.image","body":"look!","filename":"cat.png","format":"org.matrix.custom.html",
                    "formatted_body":"<b>look!</b>","file":{"url":"mxc://x/y"},"info":{"w":4},"m.mentions":{},
                    "m.relates_to":{"m.in_reply_to":{"event_id":"${'$'}a"}}}""",
                ),
                null,
            )!!
        assertEquals("m.image", base.str("msgtype"))
        assertEquals("cat.png", base.str("body"))
        assertEquals("cat.png", base.str("filename"))
        assertNotNull(base["file"])
        assertNotNull(base["info"])
        for (key in listOf("format", "formatted_body", "m.mentions", "m.relates_to")) assertNull(key, base[key])
        assertNull(extra)
    }

    @Test
    fun `an uncaptioned file's body is its name, and its spoiler mark goes beside`() {
        val (base, extra) =
            forwardContent(json("""{"msgtype":"m.video","body":"v.mp4","url":"mxc://x/v","$SPOILER":true}"""), null)!!
        assertEquals("v.mp4", base.str("body"))
        assertEquals("v.mp4", base.str("filename"))
        assertEquals("true", extra?.str(SPOILER))
    }

    @Test
    fun `a gallery's item goes as a message of its own`() {
        val gallery =
            json(
                """{"msgtype":"dm.filament.gallery","body":"trip","itemtypes":[
                {"itemtype":"m.image","body":"a.jpg","url":"mxc://x/a"},
                {"itemtype":"m.image","body":"b.jpg","url":"mxc://x/b"}]}""",
            )
        val (base, _) = forwardContent(gallery, 1)!!
        assertEquals("m.image", base.str("msgtype"))
        assertEquals("b.jpg", base.str("body"))
        assertEquals("mxc://x/b", base.str("url"))
        assertNull(base["itemtype"])
        assertNull(forwardContent(gallery, 2))
    }

    @Test
    fun `an edit forwards its newest content, and text has nothing to forward`() {
        val edited =
            json(
                """{"msgtype":"m.image","body":"* old","url":"mxc://x/old",
                "m.new_content":{"msgtype":"m.image","body":"new.png","url":"mxc://x/new"}}""",
            )
        assertEquals("mxc://x/new", forwardContent(edited, null)!!.first.str("url"))
        assertNull(forwardContent(json("""{"msgtype":"m.text","body":"hi"}"""), null))
    }
}
