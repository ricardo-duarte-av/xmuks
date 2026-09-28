package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode

/**
 * What an open room sends. Messages and deletions go through the durable [Outbox] (never lost,
 * never doubled); read receipts and typing are fire-and-forget.
 */
class RoomWriter(
    private val roomId: String,
    private val exec: ExecClient,
    private val outbox: Outbox,
) {
    /**
     * Sends [text] (markdown, `/me`, `/notice`…; gomuks renders it) through the durable outbox,
     * as a reply to [replyTo] or as an edit of [editing] (our own message).
     */
    suspend fun send(
        text: String,
        replyTo: ReplyTarget? = null,
        editing: String? = null,
    ) {
        outbox.sendMessage(
            roomId,
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("text", JsonPrimitive(text))
                when {
                    editing != null -> {
                        put(
                            "relates_to",
                            buildJsonObject {
                                put("rel_type", JsonPrimitive("m.replace"))
                                put("event_id", JsonPrimitive(editing))
                            }
                        )
                    }

                    replyTo != null -> {
                        val inReplyTo = buildJsonObject { put("event_id", JsonPrimitive(replyTo.eventId)) }
                        put("relates_to", buildJsonObject { put("m.in_reply_to", inReplyTo) })
                        // Like gomuks web: a reply pings who wrote the original.
                        put(
                            "mentions",
                            buildJsonObject {
                                put("user_ids", JsonArray(listOf(JsonPrimitive(replyTo.sender))))
                            }
                        )
                    }
                }
            },
        )
    }

    suspend fun resend(localId: String) = outbox.resend(localId)

    suspend fun discard(localId: String) = outbox.discard(localId)

    /** Deletes (redacts) [eventId] for everyone, through the outbox like any other send. */
    suspend fun redact(eventId: String) {
        outbox.enqueue(
            roomId,
            "redact_event",
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
            },
        )
    }

    /** Marks everything up to [eventId] read (a public read receipt). */
    suspend fun markRead(eventId: String) {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("receipt_type", JsonPrimitive("m.read"))
            }
        exec.exec("mark_read", params, ExecMode.Write)
    }

    /** Typing for [timeoutMs] from now; 0 stops it. */
    suspend fun setTyping(timeoutMs: Int) {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("timeout", JsonPrimitive(timeoutMs))
            }
        exec.exec("set_typing", params, ExecMode.Write)
    }
}
