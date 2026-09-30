package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse

/** One piece of a room's media: an image, video, audio or file message, who sent it and when. */
data class GalleryItem(
    val eventId: String,
    val senderName: String,
    val timestamp: Long,
    /** [MessageContent.Image], [MessageContent.Video], [MessageContent.Audio] or [MessageContent.File]. */
    val content: MessageContent,
)

/** A page of media, newest first, and where the next (older) one starts; null when there's no more. */
data class GalleryPage(
    val items: List<GalleryItem>,
    val before: Long?,
)

/**
 * A room's media, paged backwards through its timeline with gomuks' `paginate` — on its own, never
 * touching the timeline the room screen holds. Deleted messages and edits are left out.
 */
class RoomGallery(
    private val exec: ExecClient,
    database: XmuksDatabase,
) {
    private val dao = database.roomListDao()

    /** Media older than timeline row [before] (0: the newest). */
    suspend fun page(
        roomId: String,
        before: Long,
        limit: Int = PAGE,
    ): Result<GalleryPage> {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("max_timeline_id", JsonPrimitive(before))
                put("limit", JsonPrimitive(limit))
            }
        return exec.exec("paginate", params, ExecMode.Read).toResult().mapCatching { data ->
            val page = GomuksJson.decodeFromJsonElement(PaginationResponse.serializer(), data)
            val names = names(roomId)
            val oldest = page.events.mapNotNull { it.timelineRowId.takeIf { id -> id > 0 } }.minOrNull()
            GalleryPage(
                items =
                    page.events
                        .sortedByDescending { it.timestamp }
                        .mapNotNull { event -> itemOf(event, names) },
                before = oldest?.takeIf { page.hasMore && page.events.isNotEmpty() },
            )
        }
    }

    private fun itemOf(
        event: Event,
        names: Map<String, String>,
    ): GalleryItem? {
        if (event.redactedBy != null || event.relationType == "m.replace") return null
        if (event.effectiveType != "m.room.message") return null
        val content = event.effectiveContent
        val msgtype = content.str("msgtype") ?: return null
        if (msgtype !in MEDIA) return null
        val media = mediaMessage(msgtype, content, content.str("body").orEmpty()) ?: return null
        return GalleryItem(event.eventId, names[event.sender] ?: localpart(event.sender), event.timestamp, media)
    }

    /** Senders' names in the room, from the member events we hold. */
    private suspend fun names(roomId: String): Map<String, String> =
        dao
            .memberEvents(roomId)
            .mapNotNull { row ->
                val content = runCatching { GomuksJson.parseToJsonElement(row.content) as? JsonObject }.getOrNull()
                content?.str("displayname")?.takeIf { it.isNotBlank() }?.let { row.userId to it }
            }.toMap()

    private companion object {
        const val PAGE = 100
        val MEDIA = setOf("m.image", "m.video", "m.audio", "m.file")
    }
}
