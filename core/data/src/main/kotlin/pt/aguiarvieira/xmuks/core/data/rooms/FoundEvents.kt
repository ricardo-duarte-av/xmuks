package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.galleryPreview
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** An event found away from its timeline (a notification, a search hit): where, from whom, what. */
data class FoundEvent(
    val eventId: String,
    val room: RoomSummary?,
    val roomId: String,
    val sender: String,
    val senderName: String,
    val senderAvatarMxc: String?,
    val text: String,
    val timestamp: Long,
)

/** Turns events gomuks hands back outside a timeline into [FoundEvent]s, with room and sender resolved. */
class FoundEvents(
    database: XmuksDatabase,
    private val rooms: RoomListRepository,
) {
    private val dao = database.roomListDao()

    /** The events in [json]: a list, or (newer gomuks) an object holding one under `events`. */
    fun decode(json: JsonElement?): List<Event> =
        (if (json is JsonObject) json["events"] else json)
            ?.takeIf { it !is JsonNull }
            ?.let { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), it) }
            .orEmpty()

    suspend fun resolve(events: List<Event>): List<FoundEvent> {
        val summaries = rooms.rooms(events.map { it.roomId }.distinct()).associateBy { it.roomId }
        val members = HashMap<String, Map<String, JsonObject>>()
        return events.map { event ->
            val member = members.getOrPut(event.roomId) { membersOf(event.roomId) }[event.sender]
            FoundEvent(
                eventId = event.eventId,
                room = summaries[event.roomId],
                roomId = event.roomId,
                sender = event.sender,
                senderName = member?.str("displayname")?.takeIf { it.isNotBlank() } ?: localpart(event.sender),
                senderAvatarMxc = member?.str("avatar_url"),
                text =
                    (event.localContent?.previewText ?: event.effectiveContent.str("body"))
                        ?.takeIf { it.isNotBlank() }
                        ?: galleryPreview(event.effectiveContent).orEmpty(),
                timestamp = event.timestamp,
            )
        }
    }

    /** The member events we hold for [roomId], by user: names and avatars. */
    private suspend fun membersOf(roomId: String): Map<String, JsonObject> =
        dao.memberEvents(roomId).associate { row ->
            row.userId to
                (runCatching { GomuksJson.parseToJsonElement(row.content) as? JsonObject }.getOrNull() ?: EMPTY)
        }

    private companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}
