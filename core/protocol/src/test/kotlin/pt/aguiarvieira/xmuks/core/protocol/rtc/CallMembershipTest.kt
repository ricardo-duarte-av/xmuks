package pt.aguiarvieira.xmuks.core.protocol.rtc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class CallMembershipTest {
    private fun event(
        type: String,
        content: String,
        stateKey: String? = null,
        sender: String = "@alice:example.org",
        ts: Long = 1_000_000,
        sticky: Long? = null,
    ) = Event(
        rowId = 1,
        roomId = "!room:example.org",
        eventId = "\$ev",
        sender = sender,
        type = type,
        stateKey = stateKey,
        timestamp = ts,
        content = GomuksJson.parseToJsonElement(content).jsonObject,
        stickyDurationMs = sticky,
    )

    // As Element Call (compatibility mode) sends it.
    private val legacyJson =
        """
        {"application":"m.call","call_id":"","scope":"m.room","device_id":"ABCDEF",
         "membershipID":"@alice:example.org:ABCDEF","expires":14400000,"m.call.intent":"video",
         "focus_active":{"type":"livekit","focus_selection":"multi_sfu"},
         "foci_preferred":[{"type":"livekit","livekit_service_url":"https://lk.example.org/livekit/jwt"}]}
        """

    @Test
    fun `parses a legacy membership`() {
        val m = CallMemberships.parse(event(RtcTypes.LEGACY_MEMBER, legacyJson, "_@alice:example.org_ABCDEF_m.call"))
        assertNotNull(m!!)
        assertEquals(MembershipFormat.Legacy, m.format)
        assertEquals("m.call#ROOM", m.slotId)
        assertEquals("ABCDEF", m.deviceId)
        assertEquals("@alice:example.org:ABCDEF", m.rtcIdentity)
        assertEquals("https://lk.example.org/livekit/jwt", m.transport?.livekitServiceUrl)
        assertTrue(m.isVideo)
        assertEquals(1_000_000 + 14_400_000, m.expiresAt)
        assertFalse(m.isExpired(1_000_001))
    }

    @Test
    fun `created_ts carries the original join time into updates`() {
        val json = legacyJson.replace("\"expires\":14400000", "\"expires\":28800000,\"created_ts\":500")
        val m = CallMemberships.parse(event(RtcTypes.LEGACY_MEMBER, json, "_k", ts = 9_999_999))!!
        assertEquals(500, m.createdTs)
        assertEquals(500 + 28_800_000L, m.expiresAt)
    }

    @Test
    fun `an empty legacy state event is a leave`() {
        val leave = event(RtcTypes.LEGACY_MEMBER, "{}", "_@alice:example.org_ABCDEF_m.call")
        assertNull(CallMemberships.parse(leave))
        assertTrue(CallMemberships.isLeave(leave))
        assertEquals("_@alice:example.org_ABCDEF_m.call", CallMemberships.keyOf(leave))
    }

    @Test
    fun `parses a sticky membership and hashes its identity like matrix-js-sdk`() {
        val json =
            """
            {"slot_id":"m.call#ROOM","application":{"type":"m.call","m.call.intent":"audio"},
             "member":{"user_id":"@alice:example.org","device_id":"ABCDEF","id":"b7c3e1f0-1111-4222-8333-944455556666"},
             "transports":{"published":[{"type":"livekit","livekit_service_url":"https://lk"}],"can_subscribe":["livekit"]},
             "versions":[],"msc4354_sticky_key":"b7c3e1f0-1111-4222-8333-944455556666"}
            """
        val m = CallMemberships.parse(event(RtcTypes.STICKY_MEMBER, json, sticky = 3_600_000))!!
        assertEquals(MembershipFormat.Sticky, m.format)
        assertEquals("b7c3e1f0-1111-4222-8333-944455556666", m.key)
        assertEquals("audio", m.intent)
        // Reference value: base64(sha256('["@alice:example.org","ABCDEF","b7c3…"]')) without padding.
        assertEquals("LirY0rM71dQxYkLKkFvA5f67h5h74g1Chc4+2gnsVCE", m.rtcIdentity)
        assertEquals(1_000_000 + 3_600_000L, m.expiresAt)
    }

    @Test
    fun `sticky memberships must be sent by the member they describe`() {
        val json =
            """
            {"slot_id":"m.call#ROOM","application":{"type":"m.call"},
             "member":{"user_id":"@mallory:example.org","device_id":"D","id":"x"},
             "transports":{"published":[],"can_subscribe":[]},"versions":[],"msc4354_sticky_key":"x"}
            """
        assertNull(CallMemberships.parse(event(RtcTypes.STICKY_MEMBER, json)))
    }

    @Test
    fun `a content-less sticky membership is a leave keyed by its member id`() {
        val leave = event(RtcTypes.STICKY_MEMBER, """{"slot_id":"m.call#ROOM","msc4354_sticky_key":"uuid"}""")
        assertTrue(CallMemberships.isLeave(leave))
        assertEquals("uuid", CallMemberships.keyOf(leave))
    }

    @Test
    fun `our own memberships parse back to what we meant`() {
        val transports = listOf(RtcTransport.livekit("https://lk"))
        val legacy =
            CallMemberships.legacyContent("@me:hs", "DEV", "audio", transports, expiresMs = 14_400_000, createdTs = null)
        val stateKey = CallMemberships.legacyStateKey("@me:hs", "DEV")
        assertEquals("_@me:hs_DEV_m.call", stateKey)
        val parsedLegacy = CallMemberships.parse(event(RtcTypes.LEGACY_MEMBER, legacy.toString(), stateKey, sender = "@me:hs"))!!
        assertEquals("@me:hs:DEV", parsedLegacy.rtcIdentity)
        assertEquals("m.call#ROOM", parsedLegacy.slotId)

        val sticky = CallMemberships.stickyContent("@me:hs", "DEV", "uuid", "video", transports)
        val parsedSticky = CallMemberships.parse(event(RtcTypes.STICKY_MEMBER, sticky.toString(), sender = "@me:hs"))!!
        assertEquals(CallMemberships.stickyIdentity("@me:hs", "DEV", "uuid"), parsedSticky.rtcIdentity)
        assertTrue(parsedSticky.isVideo)
        assertTrue(CallMemberships.isLeave(event(RtcTypes.STICKY_MEMBER, CallMemberships.stickyLeaveContent("uuid").toString())))
    }

    @Test
    fun `rooms of MSC3757 versions use unprefixed state keys`() {
        assertEquals("@me:hs_DEV_m.call", CallMemberships.legacyStateKey("@me:hs", "DEV", "org.matrix.msc3757.10"))
    }

    @Test
    fun `parses a ring and measures its lifetime from sender_ts`() {
        val content = RtcSignals.notificationContent(ring = true, intent = "video", membershipEventId = "\$m", senderTs = 1_000_500)
        val n = RtcSignals.parseNotification(event(RtcTypes.NOTIFICATION, content.toString(), sender = "@bob:hs"))!!
        assertTrue(n.ring)
        assertTrue(n.isVideo)
        assertTrue(n.mentions("@alice:example.org"))
        assertEquals("\$m", n.membershipEventId)
        assertEquals(1_000_500 + RtcSignals.RING_LIFETIME_MS, n.expiresAt)
    }

    @Test
    fun `a sender clock far ahead falls back to the server timestamp, and lifetime is capped`() {
        val content =
            RtcSignals.notificationContent(ring = false, intent = "audio", membershipEventId = "\$m", senderTs = 1_000_000 + 60_000, lifetimeMs = 600_000)
        val n = RtcSignals.parseNotification(event(RtcTypes.NOTIFICATION, content.toString()))!!
        assertFalse(n.ring)
        assertEquals(1_000_000 + RtcSignals.MAX_LIFETIME_MS, n.expiresAt)
    }

    @Test
    fun `media key events round-trip`() {
        val content = MediaKeys.content("!r", "DEV", "@me:hs:DEV", 3, "a2V5", 42)
        val key = MediaKeys.parse("@me:hs", content)!!
        assertEquals(3, key.index)
        assertEquals("a2V5", key.keyBase64)
        assertEquals("DEV", key.deviceId)
        assertNull(MediaKeys.parse("@me:hs", JsonObject(emptyMap())))
    }
}
