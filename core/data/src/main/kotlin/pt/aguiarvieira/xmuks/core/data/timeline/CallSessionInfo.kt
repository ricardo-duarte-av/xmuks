package pt.aguiarvieira.xmuks.core.data.timeline

import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/** One call as the timeline tells it: from the first join into an empty call to the last leave. */
data class CallSessionInfo(
    /** The join that started it: where its card sits. */
    val anchorEventId: String,
    val starter: String,
    val startedAt: Long,
    /** Null while the timeline hasn't seen it end. */
    val endedAt: Long?,
    val video: Boolean,
    val participants: Set<String>,
)

/**
 * Finds the calls in a stretch of timeline from its membership events (legacy state and sticky,
 * joins and leaves), oldest first. Only what the loaded timeline holds: a call that began before it
 * shows no card (the room's banner covers calls going on now).
 */
fun callSessions(events: List<Event>): Map<String, CallSessionInfo> {
    val tracker = SessionTracker()
    events
        .filter { it.effectiveType == RtcTypes.LEGACY_MEMBER || it.effectiveType == RtcTypes.STICKY_MEMBER }
        .forEach(tracker::apply)
    return tracker.sessions
}

private class SessionTracker {
    val sessions = LinkedHashMap<String, CallSessionInfo>()
    private val inCall = HashMap<String, String>() // membership key → user
    private var current: CallSessionInfo? = null

    fun apply(event: Event) {
        val key = CallMemberships.keyOf(event) ?: return
        val membership = if (CallMemberships.isLeave(event)) null else CallMemberships.parse(event)
        if (membership != null && membership.slotId == CallMemberships.ROOM_SLOT_ID) {
            val session =
                current ?: CallSessionInfo(event.eventId, membership.userId, event.timestamp, null, false, emptySet())
            inCall[key] = membership.userId
            current =
                session.copy(
                    video = session.video || membership.isVideo,
                    participants =
                        session.participants + membership.userId
                )
        } else if (inCall.remove(key) != null && inCall.isEmpty()) {
            current?.let { sessions[it.anchorEventId] = it.copy(endedAt = event.timestamp) }
            current = null
        }
        current?.let { sessions[it.anchorEventId] = it }
    }
}
