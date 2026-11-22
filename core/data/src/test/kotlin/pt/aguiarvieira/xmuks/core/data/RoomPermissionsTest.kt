package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomPermissions
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class RoomPermissionsTest {
    private var row = 0

    private fun ev(
        type: String,
        content: String,
    ) = """{"rowid": ${++row}, "room_id": "!r", "event_id": "${'$'}e$row", "sender": "@admin:x",
           "type": "$type", "state_key": "", "content": $content}"""

    private fun state(vararg events: String): List<Event> = GomuksJson.decodeFromString(ListSerializer(Event.serializer()), "[${events.joinToString()}]")

    private val create = ev("m.room.create", """{"room_version": "10"}""")
    private val encryption = ev("m.room.encryption", """{"algorithm": "m.megolm.v1.aes-sha2"}""")
    private val levels =
        ev(
            "m.room.power_levels",
            """{"users": {"@admin:x": 100, "@mod:x": 50, "@member:x": 10}, "users_default": 0,
                "events_default": 0, "redact": 50, "state_default": 50,
                "events": {"m.reaction": 10, "m.sticker": 20, "m.room.encrypted": 10,
                    "org.matrix.msc3381.poll.start": 20, "m.room.redaction": 10, "m.room.pinned_events": 50}}""",
        )

    private fun of(
        me: String,
        vararg extra: String,
    ) = RoomPermissions.of("!r", state(create, levels, *extra), me)

    @Test
    fun `each action checks its own event type`() {
        val nobody = of("@nobody:x")
        assertNull(nobody.message)
        assertEquals(Limit.NoPermission(10, 0), nobody.reaction)
        assertEquals(Limit.NoPermission(20, 0), nobody.sticker)
        assertEquals(Limit.NoPermission(20, 0), nobody.pollStart)
        assertNull(nobody.pollVote(stable = false))
        assertEquals(Limit.NoPermission(10, 0), nobody.deleteOwn)
        assertEquals(Limit.NoPermission(50, 0), nobody.pin)
    }

    @Test
    fun `encrypted rooms check m_room_encrypted, but not for reactions or redactions`() {
        val nobody = of("@nobody:x", encryption)
        assertEquals(Limit.NoPermission(10, 0), nobody.message)
        assertEquals(Limit.NoPermission(10, 0), nobody.sticker)
        assertEquals(Limit.NoPermission(10, 0), nobody.pollStart)
        assertEquals(Limit.NoPermission(10, 0), nobody.reaction)
        val member = of("@member:x", encryption)
        assertNull(member.message)
        assertNull(member.sticker)
    }

    @Test
    fun `deleting others needs the redact level, our own only the redaction event's`() {
        val member = of("@member:x")
        assertNull(member.deleteOwn)
        assertEquals(Limit.NoPermission(50, 10), member.deleteOthers)
        assertNull(of("@mod:x").deleteOthers)
    }

    @Test
    fun `until the state is in, nothing is held back`() {
        val unknown = RoomPermissions.of("!r", emptyList(), "@nobody:x")
        assertNull(unknown.message)
        assertNull(unknown.deleteOthers)
        assertNull(unknown.pin)
    }
}
