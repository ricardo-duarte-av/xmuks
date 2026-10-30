package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.eventSource

class EventSourceTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `the whole row is shown, gomuks' own fields too`() {
        val row = json("""{"rowid":7,"type":"m.room.message","content":{"body":"hi"},"local_content":{"x":1}}""")
        assertEquals(row, json(eventSource(row)))
    }

    @Test
    fun `an encrypted event shows its cleartext, and what was sent under encrypted`() {
        val row =
            json(
                """{"rowid":7,"type":"m.room.encrypted","content":{"ciphertext":"x"},
                "decrypted":{"body":"hi"},"decrypted_type":"m.room.message","pending":false}""",
            )
        val source = json(eventSource(row))
        assertEquals(JsonPrimitive("m.room.message"), source["type"])
        assertEquals(json("""{"body":"hi"}"""), source["content"])
        assertEquals(json("""{"ciphertext":"x"}"""), source["encrypted"])
        assertNull(source["decrypted"])
        assertNull(source["decrypted_type"])
        assertEquals(JsonPrimitive(7), source["rowid"])
    }

    @Test
    fun `it's indented two spaces`() {
        assertEquals("{\n  \"rowid\"", eventSource(json("""{"rowid":7}""")).take(11))
    }
}
