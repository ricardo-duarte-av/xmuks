package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** One notification: a message that mentioned us (or would notify), where and from whom. */
data class Mention(
    val eventId: String,
    val room: RoomSummary?,
    val roomId: String,
    val sender: String,
    val senderName: String,
    val senderAvatarMxc: String?,
    val text: String,
    val timestamp: Long,
)

/** Which notifications: those that highlighted (mentions, keywords), or everything that notified. */
enum class MentionKind(
    /** gomuks' unread type bits. */
    val unreadType: Int,
) {
    Mentions(HIGHLIGHT),
    All(NOTIFY),
}

private const val NOTIFY = 0b0010
private const val HIGHLIGHT = 0b0100

/** Past notifications, newest first, from gomuks' own record of them (`get_mentions`). */
class Mentions(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val rooms: RoomListRepository,
) {
    private val dao = database.roomListDao()

    /** Up to [limit] notifications older than [before] (a timestamp), optionally in one room. */
    suspend fun page(
        kind: MentionKind,
        before: Long,
        limit: Int = PAGE,
        roomId: String? = null,
    ): Result<List<Mention>> {
        val params =
            buildJsonObject {
                put("max_timestamp", JsonPrimitive(before))
                put("type", JsonPrimitive(kind.unreadType))
                put("limit", JsonPrimitive(limit))
                roomId?.let { put("room_id", JsonPrimitive(it)) }
            }
        return exec.exec("get_mentions", params, ExecMode.Read).toResult().mapCatching { data ->
            // Newer gomuks wraps them ({events, related_events}); older answers with the list.
            val events =
                (if (data is JsonObject) data["events"] else data)
                    ?.takeIf { it !is JsonNull }
                    ?.let { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), it) }
                    .orEmpty()
            val summaries = rooms.rooms(events.map { it.roomId }.distinct()).associateBy { it.roomId }
            val members = HashMap<String, Map<String, JsonObject>>()
            events.map { event ->
                val member = members.getOrPut(event.roomId) { membersOf(event.roomId) }[event.sender]
                Mention(
                    eventId = event.eventId,
                    room = summaries[event.roomId],
                    roomId = event.roomId,
                    sender = event.sender,
                    senderName = member?.str("displayname")?.takeIf { it.isNotBlank() } ?: localpart(event.sender),
                    senderAvatarMxc = member?.str("avatar_url"),
                    text = event.localContent?.previewText ?: event.effectiveContent.str("body").orEmpty(),
                    timestamp = event.timestamp,
                )
            }
        }
    }

    /** The member events we hold for [roomId], by user: names and avatars. */
    private suspend fun membersOf(roomId: String): Map<String, JsonObject> =
        dao.memberEvents(roomId).associate { row ->
            row.userId to
                (runCatching { GomuksJson.parseToJsonElement(row.content) as? JsonObject }.getOrNull() ?: EMPTY)
        }

    private companion object {
        const val PAGE = 30
        val EMPTY = JsonObject(emptyMap())
    }
}
