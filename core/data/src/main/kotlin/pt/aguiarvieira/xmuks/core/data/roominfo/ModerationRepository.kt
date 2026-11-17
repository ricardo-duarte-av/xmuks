package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse

/** Where someone stands in a room, for moderating them: who may do what there, and their membership. */
data class MemberStanding(
    val roomName: String?,
    val levels: PowerLevels,
    /** [Membership.Leave] too when gomuks has no member event for them. */
    val membership: Membership,
)

/** Something someone sent, as far as deleting it goes: whether it's state (a "system message": joins, renames…). */
data class SentEvent(
    val eventId: String,
    val state: Boolean,
)

/**
 * Moderating one member of a room from their profile: where they stand there
 * (`get_specific_room_state`, without loading the whole member list), what they sent recently
 * (`paginate`, as gomuks' own "redact recent messages" uses the loaded timeline) and deleting it.
 */
class ModerationRepository(
    private val exec: ExecClient,
    private val outbox: Outbox,
) {
    suspend fun standing(
        roomId: String,
        userId: String,
    ): Result<MemberStanding> {
        val keys =
            listOf("m.room.create" to "", PowerLevels.TYPE to "", "m.room.name" to "", MEMBER to userId).map { (t, k) ->
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("type", JsonPrimitive(t))
                    put("state_key", JsonPrimitive(k))
                }
            }
        val params = buildJsonObject { put("keys", JsonArray(keys)) }
        return exec.exec("get_specific_room_state", params, ExecMode.Read).toResult().mapCatching { data ->
            val events = GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), data)
            val info = RoomInfo.parse(roomId, events)
            MemberStanding(info.name, info.powerLevels, info.member(userId)?.membership ?: Membership.Leave)
        }
    }

    /**
     * [userId]'s events among the room's newest [pages] pages, newest first: what's not deleted yet
     * (and isn't itself a deletion). Read from gomuks' database, so quick for recent history.
     */
    suspend fun recentEvents(
        roomId: String,
        userId: String,
        pages: Int = RECENT_PAGES,
    ): Result<List<SentEvent>> =
        runCatching {
            val found = LinkedHashMap<String, SentEvent>()
            var before = 0L
            var left = pages
            while (left-- > 0) {
                val params =
                    buildJsonObject {
                        put("room_id", JsonPrimitive(roomId))
                        put("max_timeline_id", JsonPrimitive(before))
                        put("limit", JsonPrimitive(PAGE))
                    }
                val data = exec.exec("paginate", params, ExecMode.Read).toResult().getOrThrow()
                val page = GomuksJson.decodeFromJsonElement(PaginationResponse.serializer(), data)
                page.events
                    .sortedByDescending { it.timestamp }
                    .filter { it.sender == userId && it.redactedBy == null && it.eventId.startsWith("$") }
                    .filter { it.effectiveType != PowerLevels.REDACTION }
                    .forEach { found.getOrPut(it.eventId) { SentEvent(it.eventId, it.stateKey != null) } }
                val oldest = page.events.mapNotNull { it.timelineRowId.takeIf { id -> id > 0 } }.minOrNull()
                if (!page.hasMore || oldest == null) break
                before = oldest
            }
            found.values.toList()
        }

    /**
     * Deletes [events] through the outbox, one redaction each: they go out even if the screen is
     * left or we're offline, and never twice.
     */
    suspend fun redact(
        roomId: String,
        events: List<String>,
        reason: String?,
    ) {
        events.forEach { eventId ->
            val params =
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("event_id", JsonPrimitive(eventId))
                    reason?.takeIf { it.isNotBlank() }?.let { put("reason", JsonPrimitive(it)) }
                }
            outbox.enqueue(roomId, "redact_event", params)
        }
    }

    private companion object {
        const val MEMBER = "m.room.member"
        const val PAGE = 100
        const val RECENT_PAGES = 5
    }
}
