package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomPreview
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class RoomInfoTest {
    private fun state(
        version: String,
        vararg extra: String,
    ): List<Event> {
        var row = 0

        fun ev(
            type: String,
            key: String,
            sender: String,
            content: String,
        ) = """{"rowid": ${++row}, "room_id": "!r", "event_id": "${'$'}e$row", "sender": "$sender",
               "type": "$type", "state_key": "$key", "content": $content}"""
        val events =
            listOf(
                ev("m.room.create", "", "@alice:x", """{"room_version": "$version", "additional_creators": ["@carol:x"]}"""),
                ev("m.room.name", "", "@alice:x", """{"name": "Test room"}"""),
                ev("m.room.join_rules", "", "@alice:x", """{"join_rule": "knock"}"""),
                ev("m.room.encryption", "", "@alice:x", """{"algorithm": "m.megolm.v1.aes-sha2"}"""),
                ev("m.room.member", "@alice:x", "@alice:x", """{"membership": "join", "displayname": "Alice"}"""),
                ev("m.room.member", "@bob:x", "@bob:x", """{"membership": "join"}"""),
                ev("m.room.member", "@carol:x", "@carol:x", """{"membership": "join", "displayname": "Carol"}"""),
                ev("m.room.member", "@dan:x", "@dan:x", """{"membership": "knock", "reason": "hi"}"""),
                ev("m.room.member", "@eve:x", "@alice:x", """{"membership": "ban", "reason": "spam"}"""),
            ) + extra.mapIndexed { i, c -> c.replace("ROW", "${100 + i}") }
        return GomuksJson.decodeFromString(ListSerializer(Event.serializer()), "[${events.joinToString()}]")
    }

    private val levels =
        """{"rowid": ROW, "room_id": "!r", "event_id": "${'$'}pl", "sender": "@alice:x", "type": "m.room.power_levels",
            "state_key": "", "content": {"users": {"@alice:x": 100, "@bob:x": 50}, "users_default": 0,
            "events": {"m.room.name": 50}, "state_default": 100, "kick": 50, "ban": 50, "invite": 0}}"""

    @Test
    fun `room details and members sorted by level`() {
        val info = RoomInfo.parse("!r", state("10", levels))
        assertEquals("Test room", info.name)
        assertEquals("knock", info.joinRule)
        assertTrue(info.encrypted)
        assertEquals(listOf("@alice:x", "@bob:x", "@carol:x", "@dan:x", "@eve:x"), info.members.map { it.userId })
        assertEquals(1, info.members(Membership.Knock).size)
        assertEquals("spam", info.member("@eve:x")?.reason)
        assertEquals("@bob:x", info.member("@bob:x")?.name)
    }

    @Test
    fun `moderation follows levels, and only downwards`() {
        val pl = RoomInfo.parse("!r", state("10", levels)).powerLevels
        assertTrue(pl.canKick("@alice:x", "@bob:x"))
        assertFalse(pl.canKick("@bob:x", "@alice:x"))
        assertTrue(pl.canBan("@bob:x", "@carol:x"))
        assertTrue(pl.canSetState("@bob:x", "m.room.name"))
        assertFalse(pl.canSetState("@bob:x", "m.room.topic"))
        assertTrue(pl.canChangeLevel("@alice:x", "@bob:x", 100))
        assertFalse(pl.canChangeLevel("@bob:x", "@carol:x"))
        assertFalse(pl.canChangeLevel("@alice:x", "@bob:x", 101))
    }

    @Test
    fun `from room version 12 creators outrank everyone`() {
        val pl = RoomInfo.parse("!r", state("12", levels)).powerLevels
        assertEquals(PowerLevels.CREATOR, pl.of("@alice:x"))
        assertEquals(PowerLevels.CREATOR, pl.of("@carol:x"))
        assertFalse(pl.canKick("@carol:x", "@alice:x"))
        assertFalse(pl.canChangeLevel("@alice:x", "@carol:x"))
    }

    @Test
    fun `without power levels the creator is admin and state is open`() {
        val pl = RoomInfo.parse("!r", state("10")).powerLevels
        assertEquals(100L, pl.of("@alice:x"))
        assertEquals(0L, pl.of("@bob:x"))
        assertTrue(pl.canSetState("@bob:x", "m.room.topic"))
    }

    @Test
    fun `a new level keeps the rest of the content, and the default removes the entry`() {
        val pl = RoomInfo.parse("!r", state("10", levels)).powerLevels
        val promoted = pl.withUser("@carol:x", 50)
        assertEquals(50L, promoted["users"]!!.jsonObject["@carol:x"]!!.jsonPrimitive.long)
        assertEquals(100L, promoted["state_default"]!!.jsonPrimitive.long)
        assertNull(pl.withUser("@bob:x", 0)["users"]!!.jsonObject["@bob:x"])
    }

    @Test
    fun `a room summary as a server returns it`() {
        val json =
            GomuksJson
                .parseToJsonElement(
                    """{"room_id":"!spec","avatar_url":"mxc://matrix.org/x","canonical_alias":"#matrix-spec:matrix.org",
                    "guest_can_join":true,"join_rule":"public","name":"Matrix Spec","num_joined_members":1002,
                    "room_type":"","topic":"Discuss the spec","world_readable":true,"room_version":"12","membership":"leave"}""",
                ).jsonObject
        val preview = RoomPreview.parse(json)
        assertEquals("Matrix Spec", preview.name)
        assertEquals(1002, preview.joinedMembers)
        assertEquals("public", preview.joinRule)
        assertEquals("leave", preview.membership)
        assertTrue(preview.worldReadable)
        assertFalse(preview.isSpace)
        assertFalse(preview.canKnock)
    }
}
