package pt.aguiarvieira.xmuks.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.rooms.inviteOf
import pt.aguiarvieira.xmuks.core.database.InvitedRoomEntity

class InvitesTest {
    private fun entity(
        state: String,
        inviter: String? = null,
    ) = InvitedRoomEntity(
        roomId = "!room:x",
        createdAt = 1791486564355,
        name = null,
        avatar = null,
        inviter = inviter,
        isDirect = false,
        inviteState = state,
        generation = 1,
    )

    @Test
    fun `a DM invite, as gomuks sent Toby_A's`() {
        val invite =
            inviteOf(
                entity(
                    """
                    [{"state_key":"","sender":"@toby_a_1990:matrix.org","type":"m.room.create",
                      "content":{"additional_creators":["@daedric:aguiarvieira.pt"],"room_version":"12"}},
                     {"state_key":"","sender":"@toby_a_1990:matrix.org","type":"m.room.join_rules",
                      "content":{"join_rule":"invite"}},
                     {"state_key":"","sender":"@toby_a_1990:matrix.org","type":"m.room.encryption",
                      "content":{"algorithm":"m.megolm.v1.aes-sha2"}},
                     {"state_key":"@toby_a_1990:matrix.org","sender":"@toby_a_1990:matrix.org","type":"m.room.member",
                      "content":{"displayname":"Toby_A","membership":"join"}},
                     {"state_key":"@daedric:aguiarvieira.pt","sender":"@toby_a_1990:matrix.org","type":"m.room.member",
                      "origin_server_ts":1791486564355,"event_id":"${'$'}x",
                      "content":{"avatar_url":"mxc://aguiarvieira.pt/me","displayname":"Ricardo Duarte",
                                 "is_direct":true,"membership":"invite"}}]
                    """.trimIndent(),
                ),
            )
        assertEquals("Toby_A", invite.name)
        assertEquals("@toby_a_1990:matrix.org", invite.inviterId)
        assertEquals("Toby_A", invite.inviterName)
        assertTrue(invite.isDirect)
        assertFalse(invite.isSpace)
        assertEquals("m.megolm.v1.aes-sha2", invite.encryption)
        assertEquals("invite", invite.joinRule)
        assertEquals("12", invite.roomVersion)
        assertEquals(1, invite.joinedMembers)
        // Our own avatar on our invite is not the room's.
        assertNull(invite.avatarMxc)
        assertNull(invite.reason)
    }

    @Test
    fun `a named room keeps its name and avatar, and says why`() {
        val invite =
            inviteOf(
                entity(
                    """
                    [{"state_key":"","type":"m.room.name","content":{"name":"Climbing"}},
                     {"state_key":"","type":"m.room.avatar","content":{"url":"mxc://x/room"}},
                     {"state_key":"","type":"m.room.canonical_alias","content":{"alias":"#climb:x"}},
                     {"state_key":"","type":"m.room.topic","content":{"topic":"Walls"}},
                     {"state_key":"@a:x","sender":"@a:x","type":"m.room.member",
                      "content":{"displayname":"Ana","avatar_url":"mxc://x/ana","membership":"join"}},
                     {"state_key":"@b:x","sender":"@b:x","type":"m.room.member","content":{"membership":"join"}},
                     {"state_key":"@me:x","sender":"@a:x","type":"m.room.member",
                      "content":{"membership":"invite","reason":"come climb"}}]
                    """.trimIndent(),
                ),
            )
        assertEquals("Climbing", invite.name)
        assertEquals("mxc://x/room", invite.avatarMxc)
        assertEquals("#climb:x", invite.canonicalAlias)
        assertEquals("Walls", invite.topic)
        assertEquals("Ana", invite.inviterName)
        assertEquals(2, invite.joinedMembers)
        assertEquals("come climb", invite.reason)
        assertFalse(invite.isDirect)
        assertNull(invite.encryption)
    }

    @Test
    fun `a DM without a name takes the inviter's avatar`() {
        val invite =
            inviteOf(
                entity(
                    """
                    [{"state_key":"@a:x","sender":"@a:x","type":"m.room.member",
                      "content":{"displayname":"Ana","avatar_url":"mxc://x/ana","membership":"join"}},
                     {"state_key":"@me:x","sender":"@a:x","type":"m.room.member",
                      "content":{"membership":"invite","is_direct":true}}]
                    """.trimIndent(),
                ),
            )
        assertEquals("Ana", invite.name)
        assertEquals("mxc://x/ana", invite.avatarMxc)
    }

    @Test
    fun `a space`() {
        val invite =
            inviteOf(
                entity(
                    """[{"state_key":"","type":"m.room.create","content":{"type":"m.space"}},
                        {"state_key":"","type":"m.room.name","content":{"name":"Home"}}]""",
                    inviter = "@a:x",
                ),
            )
        assertTrue(invite.isSpace)
        assertEquals("@a:x", invite.inviterId)
    }

    @Test
    fun `unreadable state still lists the invite`() {
        val invite = inviteOf(entity("not json", inviter = "@a:x"))
        assertEquals("@a:x", invite.name)
    }
}
