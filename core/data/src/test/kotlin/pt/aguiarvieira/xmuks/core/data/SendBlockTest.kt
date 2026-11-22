package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.roominfo.SendBlock
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class SendBlockTest {
    private var row = 0

    private fun ev(
        type: String,
        sender: String,
        content: String,
    ) = """{"rowid": ${++row}, "room_id": "!r", "event_id": "${'$'}e$row", "sender": "$sender",
           "type": "$type", "state_key": "", "content": $content}"""

    private fun state(vararg events: String): List<Event> = GomuksJson.decodeFromString(ListSerializer(Event.serializer()), "[${events.joinToString()}]")

    private val create = ev("m.room.create", "@nex:a.uk", """{"room_version": "10"}""")

    // As sent in the room that showed the bug: everyone not listed is at -1, messages need 50.
    private val levels =
        ev(
            "m.room.power_levels",
            "@nex:a.uk",
            """{"ban": 50, "events": {"m.room.encrypted": 300, "m.room.encryption": 9007199254740991,
                "m.room.tombstone": 200}, "events_default": 50, "invite": 50, "kick": 50, "redact": 50,
                "state_default": 50, "users": {"@nex:a.uk": 200, "@mod:a.uk": 50}, "users_default": -1}""",
        )

    @Test
    fun `users_default below events_default keeps the unlisted from sending`() {
        assertEquals(SendBlock.NoPermission(50, -1), SendBlock.of("!r", state(create, levels), "@me:x"))
        assertNull(SendBlock.of("!r", state(create, levels), "@mod:a.uk"))
    }

    @Test
    fun `an encrypted room checks m_room_encrypted`() {
        val encryption = ev("m.room.encryption", "@nex:a.uk", """{"algorithm": "m.megolm.v1.aes-sha2"}""")
        assertEquals(
            SendBlock.NoPermission(300, 200),
            SendBlock.of("!r", state(create, levels, encryption), "@nex:a.uk"),
        )
    }

    @Test
    fun `a tombstone wins, and points at the new room through its sender's server`() {
        val tombstone =
            ev(
                SendBlock.TOMBSTONE,
                "@nex:starstruck.systems",
                """{"body": "This room has been replaced", "replacement_room": "!new"}""",
            )
        assertEquals(
            SendBlock.Replaced("!new", listOf("starstruck.systems")),
            SendBlock.of("!r", state(create, levels, tombstone), "@nex:a.uk"),
        )
    }

    @Test
    fun `without power levels anyone may send`() {
        assertNull(SendBlock.of("!r", state(create), "@me:x"))
    }

    @Test
    fun `room links carry via servers`() {
        assertEquals("matrix:roomid/new%3Ax.org?via=x.org", LinkResolver.roomUri("!new:x.org", via = listOf("x.org")))
    }
}
