package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.callSessions
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

class CallSessionsTest {
    private var n = 0L
    private val transports = listOf(RtcTransport.livekit("https://lk"))

    private fun join(
        user: String,
        ts: Long,
        intent: String = "audio",
    ) = state(user, ts, CallMemberships.legacyContent(user, "D", intent, transports, 14_400_000, null))

    private fun leave(
        user: String,
        ts: Long,
    ) = state(user, ts, JsonObject(emptyMap()))

    private fun state(
        user: String,
        ts: Long,
        content: JsonObject,
    ) = Event(
        rowId = ++n,
        roomId = "!r",
        eventId = "\$e$n",
        sender = user,
        type = RtcTypes.LEGACY_MEMBER,
        stateKey = CallMemberships.legacyStateKey(user, "D"),
        timestamp = ts,
        content = content,
    )

    @Test
    fun `one session from the first join to the last leave`() {
        val events =
            listOf(
                join("@a:hs", 1_000),
                join("@b:hs", 2_000, intent = "video"),
                leave("@a:hs", 3_000),
                leave("@b:hs", 61_000),
            )
        val sessions = callSessions(events)
        assertEquals(1, sessions.size)
        val call = sessions.getValue("\$e1")
        assertEquals("@a:hs", call.starter)
        assertEquals(61_000L, call.endedAt)
        assertTrue(call.video)
        assertEquals(setOf("@a:hs", "@b:hs"), call.participants)
    }

    @Test
    fun `a call still going has no end, and a later one is a new session`() {
        val events = listOf(join("@a:hs", 1_000), leave("@a:hs", 2_000), join("@b:hs", 5_000))
        val sessions = callSessions(events)
        assertEquals(listOf("\$e1", "\$e3"), sessions.keys.toList())
        assertNull(sessions.getValue("\$e3").endedAt)
    }

    @Test
    fun `a leave for a call that started before the timeline is ignored`() {
        assertTrue(callSessions(listOf(leave("@a:hs", 1_000))).isEmpty())
    }
}
