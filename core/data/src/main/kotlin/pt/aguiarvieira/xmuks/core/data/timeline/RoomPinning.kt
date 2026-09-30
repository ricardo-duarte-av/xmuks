package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event

/** A room's pins: changing them (`set_state`), and the pinned messages as timeline items. */
class RoomPinning(
    private val roomId: String,
    private val exec: ExecClient,
    private val pins: Flow<Pins>,
    private val getEvent: suspend (String) -> Event?,
    private val items: suspend (TimelineSnapshot) -> List<TimelineItem>,
) {
    /** Pins or unpins [eventId] (newest pins last, as other clients add them). */
    suspend fun setPinned(
        eventId: String,
        pinned: Boolean,
    ): Boolean {
        val current = pins.first().eventIds
        val updated = if (pinned) (current - eventId) + eventId else current - eventId
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(Pins.TYPE))
                put("state_key", JsonPrimitive(""))
                put("content", buildJsonObject { put("pinned", JsonArray(updated.map(::JsonPrimitive))) })
            }
        return exec.exec("set_state", params, ExecMode.Write) is ExecResult.Ok
    }

    /** The pinned messages themselves, newest pin first (whichever gomuks can get). */
    suspend fun pinnedItems(eventIds: List<String>): List<TimelineItem> {
        val events = eventIds.asReversed().mapNotNull { getEvent(it) }
        var standIn = -1L
        val keyed = events.map { if (it.rowId != 0L) it else it.copy(rowId = standIn--) }
        val snapshot =
            TimelineSnapshot(roomId, events = keyed, eventsByRowId = keyed.associateBy { it.rowId }, loaded = true)
        return items(snapshot)
    }
}
