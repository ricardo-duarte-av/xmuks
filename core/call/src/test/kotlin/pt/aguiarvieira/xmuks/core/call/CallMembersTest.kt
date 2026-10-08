package pt.aguiarvieira.xmuks.core.call

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.call.signalling.CallMembers
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

class CallMembersTest {
    private val room = "!r:hs"
    private val transports = listOf(RtcTransport.livekit("https://lk"))
    private var row = 0L

    private fun legacy(
        user: String,
        device: String,
        ts: Long,
        content: JsonObject = CallMemberships.legacyContent(user, device, "audio", transports, 14_400_000, null),
    ) = Event(
        rowId = ++row,
        roomId = room,
        eventId = "\$l$row",
        sender = user,
        type = RtcTypes.LEGACY_MEMBER,
        stateKey = CallMemberships.legacyStateKey(user, device),
        timestamp = ts,
        content = content,
    )

    private fun sticky(
        user: String,
        device: String,
        member: String,
        ts: Long,
        leave: Boolean = false,
    ) = Event(
        rowId = ++row,
        roomId = room,
        eventId = "\$s$row",
        sender = user,
        type = RtcTypes.STICKY_MEMBER,
        timestamp = ts,
        content =
            if (leave) {
                CallMemberships.stickyLeaveContent(member)
            } else {
                CallMemberships.stickyContent(user, device, member, "video", transports)
            },
        stickyDurationMs = CallMemberships.STICKY_DURATION_MS,
    )

    @Test
    fun `merges legacy and sticky members, oldest first`() {
        val members = CallMembers(room)
        members.load(listOf(legacy("@a:hs", "A", ts = 1_000)), listOf(sticky("@b:hs", "B", "m1", ts = 2_000)))
        assertEquals(listOf("@a:hs", "@b:hs"), members.active(now = 3_000).map { it.userId })
    }

    @Test
    fun `a sticky leave removes that member, and an older join can't bring them back`() {
        val members = CallMembers(room)
        members.load(emptyList(), listOf(sticky("@b:hs", "B", "m1", ts = 2_000)))
        assertTrue(members.apply(sticky("@b:hs", "B", "m1", ts = 3_000, leave = true)))
        assertFalse(members.apply(sticky("@b:hs", "B", "m1", ts = 2_500)))
        assertTrue(members.active(now = 4_000).isEmpty())
    }

    @Test
    fun `legacy state counts only when the sync says it is current`() {
        val members = CallMembers(room)
        val join = legacy("@a:hs", "A", ts = 1_000)
        val history = legacy("@c:hs", "C", ts = 900)
        val sync =
            SyncRoom(
                state = mapOf(RtcTypes.LEGACY_MEMBER to mapOf(join.stateKey!! to join.rowId)),
                events = listOf(join, history),
            )
        assertTrue(members.apply(sync))
        assertEquals(listOf("@a:hs"), members.active(now = 2_000).map { it.userId })

        val leave = legacy("@a:hs", "A", ts = 5_000, content = JsonObject(emptyMap()))
        members.apply(SyncRoom(state = mapOf(RtcTypes.LEGACY_MEMBER to mapOf(leave.stateKey!! to leave.rowId)), events = listOf(leave)))
        assertTrue(members.active(now = 6_000).isEmpty())
    }

    @Test
    fun `expired memberships don't count`() {
        val members = CallMembers(room)
        members.load(listOf(legacy("@a:hs", "A", ts = 1_000)), emptyList())
        assertTrue(members.active(now = 1_000 + CallMemberships.DEFAULT_LEGACY_EXPIRY_MS).isEmpty())
    }
}
