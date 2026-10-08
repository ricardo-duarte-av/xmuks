package pt.aguiarvieira.xmuks.core.call.signalling

import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMembership
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/**
 * Who is in a room's call: legacy state memberships and sticky memberships merged, keyed by the
 * slot each occupies (state key / sticky key), newest event per key winning.
 *
 * Not thread-safe; owners serialise access.
 */
class CallMembers(
    val roomId: String,
) {
    private val byKey = HashMap<String, Entry>()

    private data class Entry(
        val ts: Long,
        val membership: CallMembership?,
    )

    /** Seeds from `get_room_state` and `get_sticky_events` results. */
    fun load(
        state: List<Event>,
        sticky: List<Event>,
    ) {
        state.filter { it.type == RtcTypes.LEGACY_MEMBER }.forEach(::apply)
        sticky.filter { it.effectiveType == RtcTypes.STICKY_MEMBER }.forEach(::apply)
    }

    /**
     * Applies one sync for this room. Legacy memberships count only when the sync's state map says
     * the event is current; sticky ones whenever they're newer than what the key holds.
     */
    fun apply(room: SyncRoom): Boolean {
        val currentLegacy =
            room.state[RtcTypes.LEGACY_MEMBER]
                ?.values
                ?.toSet()
                .orEmpty()
        var changed = false
        for (event in room.events) {
            val relevant =
                when (event.effectiveType) {
                    RtcTypes.LEGACY_MEMBER -> event.rowId in currentLegacy
                    RtcTypes.STICKY_MEMBER -> true
                    else -> false
                }
            if (relevant && apply(event)) changed = true
        }
        return changed
    }

    /** Applies a single membership event (join or leave). True when the call's members changed. */
    fun apply(event: Event): Boolean {
        val key = CallMemberships.keyOf(event) ?: return false
        val old = byKey[key]
        // State is authoritative whatever its timestamp; sticky events race, so the newest wins.
        if (event.effectiveType == RtcTypes.STICKY_MEMBER && old != null && old.ts > event.timestamp) return false
        val membership = if (CallMemberships.isLeave(event)) null else CallMemberships.parse(event)
        byKey[key] = Entry(event.timestamp, membership)
        return old?.membership != membership
    }

    /** Live members of the room-wide `m.call`, oldest join first. One per user+device. */
    fun active(now: Long): List<CallMembership> =
        byKey.values
            .mapNotNull { it.membership }
            .filter {
                !it.isExpired(now) && it.application == RtcTypes.APPLICATION_CALL &&
                    it.slotId == CallMemberships.ROOM_SLOT_ID
            }.groupBy { it.userId to it.deviceId }
            .map { (_, same) -> same.maxBy { it.createdTs } }
            .sortedBy { it.createdTs }
}
