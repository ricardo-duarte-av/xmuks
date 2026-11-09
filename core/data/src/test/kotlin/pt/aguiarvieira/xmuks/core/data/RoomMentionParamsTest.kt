package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.mentionsRoom
import pt.aguiarvieira.xmuks.core.data.timeline.messageParams

class RoomMentionParamsTest {
    private fun JsonObject.mentions() = get("mentions")?.jsonObject

    @Test
    fun `an @room that pings sends m-mentions room`() {
        val mentions = messageParams("!r", "@room hi", mentionRoom = true).mentions()!!
        assertEquals("true", mentions["room"].toString())
        assertNull(mentions["user_ids"])
    }

    @Test
    fun `a plain message sends no mentions for gomuks to fill`() {
        assertNull(messageParams("!r", "@room hi").mentions())
    }

    @Test
    fun `a reply that pings the room keeps pinging the replied-to sender`() {
        val mentions = messageParams("!r", "@room", ReplyTarget("\$m", "@bob:x"), mentionRoom = true).mentions()!!
        assertEquals("true", mentions["room"].toString())
        assertEquals("[\"@bob:x\"]", mentions["user_ids"].toString())
    }

    @Test
    fun `an edit can ping the room too`() {
        val mentions = messageParams("!r", "@room", editing = "\$m", mentionRoom = true).mentions()!!
        assertEquals("true", mentions["room"].toString())
    }

    @Test
    fun `@room is found as a word of its own`() {
        assertTrue(mentionsRoom("@room"))
        assertTrue(mentionsRoom("hey @room, look"))
        assertTrue(mentionsRoom("(@room)"))
        assertFalse(mentionsRoom("@roomie"))
        assertFalse(mentionsRoom("me@room.org"))
        assertFalse(mentionsRoom("@room-mate"))
        assertFalse(mentionsRoom("no ping here"))
    }
}
