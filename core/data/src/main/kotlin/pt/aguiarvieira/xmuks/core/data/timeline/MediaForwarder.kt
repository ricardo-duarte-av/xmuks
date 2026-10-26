package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.Galleries
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * Forwards to another room: a message's media, the same file (`url`, or `file` with its keys) sent
 * again with a caption of its own, so nothing is downloaded or uploaded (the original's caption,
 * reply and relations stay behind; one of a gallery's items goes as a message of its own); or a
 * whole message, as it is.
 */
class MediaForwarder(
    private val exec: ExecClient,
    private val outbox: Outbox,
) {
    /** Sends [eventId]'s file ([index]: that gallery item's) to [toRoom]; false when it can't be had. */
    suspend fun forward(
        fromRoom: String,
        eventId: String,
        index: Int?,
        toRoom: String,
        caption: String,
    ): Boolean {
        val event = event(fromRoom, eventId) ?: return false
        val (base, extra) = forwardContent(event.effectiveContent, index) ?: return false
        outbox.sendMessage(toRoom, messageParams(toRoom, caption, baseContent = base, extra = extra))
        return true
    }

    /** Sends [eventId] to [toRoom] as it is (its newest edit); false when it can't be had. */
    suspend fun forwardMessage(
        fromRoom: String,
        eventId: String,
        toRoom: String,
    ): Boolean {
        val event = event(fromRoom, eventId) ?: return false
        val type = event.effectiveType.takeIf { it in MESSAGE_TYPES } ?: return false
        val content = messageAsIs(event.effectiveContent) ?: return false
        outbox.enqueue(
            toRoom,
            "send_event",
            buildJsonObject {
                put("room_id", JsonPrimitive(toRoom))
                put("type", JsonPrimitive(type))
                put("content", content)
            },
        )
        return true
    }

    private suspend fun event(
        roomId: String,
        eventId: String,
    ): Event? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
            }
        val result = exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        return runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), result.data) }.getOrNull()
    }
}

/**
 * What forwarding [content] sends: gomuks' `base_content` (the file and its info, named for its
 * file, so the caption gomuks writes in becomes `body`) and what that type would drop (spoiler,
 * voice and waveform), sent beside it. An edit's newest content counts; null when there's no file.
 */
internal fun forwardContent(
    content: JsonObject,
    index: Int?,
): Pair<JsonObject, JsonObject?>? {
    val current = content.obj("m.new_content") ?: content
    val source =
        if (index != null) {
            // The same items, in the same order, as the gallery shows (unshowable ones left out).
            Galleries.items(current).filter { media(it) != null }.getOrNull(index) ?: return null
        } else {
            current
        }
    if (source.str("msgtype") !in FORWARDABLE || media(source) == null) return null
    val name = source.str("filename") ?: source.str("body")?.takeIf { it.isNotBlank() } ?: "file"
    val base =
        JsonObject(
            source.filterKeys { it in BASE_KEYS } +
                mapOf("body" to JsonPrimitive(name), "filename" to JsonPrimitive(name)),
        )
    val extra = source.filterKeys { it in EXTRA_KEYS }.takeIf { it.isNotEmpty() }?.let(::JsonObject)
    return base to extra
}

/**
 * What forwarding a whole message sends: its content (an edit's newest), without what ties it to
 * where it was (a reply, a thread, an edit) or pings anyone again; null when nothing's left.
 */
internal fun messageAsIs(content: JsonObject): JsonObject? {
    val current = content.obj("m.new_content") ?: content
    return JsonObject(current.filterKeys { it !in LEFT_BEHIND }).takeIf { it.isNotEmpty() }
}

/** The events forwarded whole: messages, stickers and polls (which start afresh). */
private val MESSAGE_TYPES =
    setOf("m.room.message", "m.sticker", "m.poll.start", "org.matrix.msc3381.poll.start")

private val LEFT_BEHIND = setOf("m.relates_to", "m.new_content", "m.mentions")

private val FORWARDABLE = setOf("m.image", "m.video", "m.audio", "m.file")

/** What gomuks' typed message content keeps of a file message. */
private val BASE_KEYS = setOf("msgtype", "url", "file", "info")

/** A file message's keys that type doesn't know. */
private val EXTRA_KEYS =
    setOf(
        SPOILER,
        "$SPOILER.reason",
        "m.spoiler",
        "m.spoiler.reason",
        "org.matrix.msc1767.audio",
        "org.matrix.msc3245.voice",
    )
