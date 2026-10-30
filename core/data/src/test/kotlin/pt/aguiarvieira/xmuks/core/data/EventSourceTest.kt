package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.eventSource
import pt.aguiarvieira.xmuks.core.protocol.Event

class EventSourceTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun event(
        content: String = "{}",
        decrypted: String? = null,
    ) = Event(
        rowId = 7,
        roomId = "!r:x",
        eventId = "\$e",
        sender = "@a:x",
        type = if (decrypted != null) "m.room.encrypted" else "m.room.message",
        timestamp = 1000,
        content = json(content),
        decrypted = decrypted?.let(::json),
        decryptedType = decrypted?.let { "m.room.message" },
    )

    @Test
    fun `an event is shown as Matrix has it, without gomuks' row`() {
        val source = json(eventSource(event("""{"msgtype":"m.text","body":"hi"}""")))
        assertEquals(JsonPrimitive("m.room.message"), source["type"])
        assertEquals(JsonPrimitive(1000), source["origin_server_ts"])
        assertEquals(json("""{"msgtype":"m.text","body":"hi"}"""), source["content"])
        assertNull(source["rowid"])
        assertNull(source["state_key"])
    }

    @Test
    fun `an encrypted event is shown as it decrypted`() {
        val source = json(eventSource(event("""{"ciphertext":"x"}""", decrypted = """{"body":"hi"}""")))
        assertEquals(JsonPrimitive("m.room.message"), source["type"])
        assertEquals(json("""{"body":"hi"}"""), source["content"])
    }

    @Test
    fun `it's indented two spaces`() {
        assertEquals("{\n  \"type\"", eventSource(event()).take(10))
    }
}
