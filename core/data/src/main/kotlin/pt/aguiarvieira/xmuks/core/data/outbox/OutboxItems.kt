package pt.aguiarvieira.xmuks.core.data.outbox

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
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
        val text = params.string("text").orEmpty()
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
                MessageContent.Text(
                    body = if (emote) text.removePrefix(EMOTE_PREFIX) else text,
                    html = null,
                    kind = if (emote) TextKind.Emote else TextKind.Text,
                    bigEmoji = false,
                ),
            reply = null,
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

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

private const val EMOTE_PREFIX = "/me "
