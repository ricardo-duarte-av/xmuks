package pt.aguiarvieira.xmuks.core.data.outbox

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyPreview
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.media
import pt.aguiarvieira.xmuks.core.data.timeline.mediaMessage
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxEntity
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxState
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * Outbox entries as timeline messages, after everything gomuks has (they're newer by definition).
 * Rendered from what we asked gomuks to send; gomuks' markdown rendering shows once it has it.
 */
fun List<OutboxEntity>.toTimelineItems(
    me: String,
    myName: String,
): List<TimelineItem.Message> =
    mapNotNull { entry ->
        if (entry.command != Outbox.SEND_MESSAGE) return@mapNotNull null
        val params =
            runCatching { GomuksJson.parseToJsonElement(entry.params).jsonObject }.getOrNull() ?: return@mapNotNull null
        val relates = params["relates_to"] as? JsonObject
        // An edit shows on its original once gomuks has it; it isn't a message of its own.
        if (relates?.string("rel_type") == "m.replace") return@mapNotNull null
        val replyTo = (relates?.get("m.in_reply_to") as? JsonObject)?.string("event_id")
        // A command's text is empty: show what was typed (its body).
        val text =
            params.string("text")?.takeIf { it.isNotEmpty() }
                ?: (params["base_content"] as? JsonObject)?.string("body").orEmpty()
        val emote = text.startsWith(EMOTE_PREFIX)
        TimelineItem.Message(
            key = "o:${entry.localId}",
            eventId = entry.localId,
            sender = me,
            label = SenderLabel(me, myName),
            senderAvatarMxc = null,
            fromMe = true,
            timestamp = entry.createdAt,
            content =
                mediaOf(params["base_content"] as? JsonObject, params.string("text").orEmpty())
                    ?: MessageContent.Text(
                        body = if (emote) text.removePrefix(EMOTE_PREFIX) else text,
                        html = null,
                        kind = if (emote) TextKind.Emote else TextKind.Text,
                        bigEmoji = false,
                    ),
            reply = replyTo?.let { ReplyPreview(it, sender = null, text = null) },
            reactions = emptyList(),
            edited = false,
            firstInGroup = true,
            lastInGroup = true,
            readBy = emptyList(),
            sendError = entry.error,
            sendState =
                when (entry.state) {
                    OutboxState.Failed.name -> SendState.Failed
                    OutboxState.Unknown.name -> SendState.Unknown
                    else -> SendState.Sending
                },
            localId = entry.localId,
        )
    }

/** An uploaded file or a sticker, as it will show; [caption] becomes its body, as gomuks does. */
private fun mediaOf(
    base: JsonObject?,
    caption: String,
): MessageContent? {
    val msgtype = base?.string("msgtype") ?: return null
    val content = if (caption.isEmpty()) base else JsonObject(base + ("body" to JsonPrimitive(caption)))
    return when (msgtype) {
        "m.sticker" -> media(content)?.let { MessageContent.Sticker(it, content.string("body").orEmpty()) }
        "m.image", "m.video", "m.audio", "m.file" -> mediaMessage(msgtype, content, content.string("body").orEmpty())
        "m.location" -> MessageContent.Location(content.string("body").orEmpty(), content.string("geo_uri"))
        else -> null
    }
}

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

private const val EMOTE_PREFIX = "/me "
