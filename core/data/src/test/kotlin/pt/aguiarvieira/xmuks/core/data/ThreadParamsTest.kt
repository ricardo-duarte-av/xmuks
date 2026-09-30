package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.messageParams

class ThreadParamsTest {
    private fun relatesTo(params: JsonObject) = params["relates_to"]!!.jsonObject

    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.content

    @Test
    fun `into a thread without answering anyone - a fallback reply to its latest, nobody pinged`() {
        val params = messageParams("!r", "hi", ReplyTarget("\$latest", "@bob:x", threadRoot = "\$root", fallback = true))
        val relation = relatesTo(params)
        assertEquals("m.thread", relation.str("rel_type"))
        assertEquals("\$root", relation.str("event_id"))
        assertEquals("true", relation.str("is_falling_back"))
        assertEquals("\$latest", relation["m.in_reply_to"]!!.jsonObject.str("event_id"))
        assertNull(params["mentions"])
    }

    @Test
    fun `answering someone in a thread - a real reply, and they're pinged`() {
        val params = messageParams("!r", "hi", ReplyTarget("\$msg", "@bob:x", threadRoot = "\$root"))
        val relation = relatesTo(params)
        assertEquals("m.thread", relation.str("rel_type"))
        assertEquals("false", relation.str("is_falling_back"))
        assertEquals("\$msg", relation["m.in_reply_to"]!!.jsonObject.str("event_id"))
        assertEquals("@bob:x", params["mentions"]!!.jsonObject["user_ids"].toString().trim('[', ']', '"'))
    }

    @Test
    fun `a plain reply has no thread`() {
        val relation = relatesTo(messageParams("!r", "hi", ReplyTarget("\$msg", "@bob:x")))
        assertNull(relation.str("rel_type"))
    }
}
