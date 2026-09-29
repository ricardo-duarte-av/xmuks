package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.emoji.PackImage
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
        outbox.sendMessage(roomId, messageParams(roomId, text, replyTo, editing))
    }

    /**
     * A location (`m.location` with its MSC3488 form alongside): our own position ([self]) or a
     * pin dropped on the map, with the fix's [accuracy] in metres when there is one.
     */
    suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        self: Boolean,
        replyTo: ReplyTarget? = null,
    ) {
        val coordinates = "%.6f,%.6f".format(java.util.Locale.ROOT, latitude, longitude)
        val geoUri = "geo:$coordinates" + (accuracy?.let { ";u=${it.toInt()}" } ?: "")
        val body = "Location: $coordinates"
        val content =
            buildJsonObject {
                put("msgtype", JsonPrimitive("m.location"))
                put("body", JsonPrimitive(body))
                put("geo_uri", JsonPrimitive(geoUri))
                put(
                    "org.matrix.msc3488.location",
                    buildJsonObject {
                        put("uri", JsonPrimitive(geoUri))
                        put("description", JsonPrimitive(body))
                    },
                )
                put(
                    "org.matrix.msc3488.asset",
                    buildJsonObject { put("type", JsonPrimitive(if (self) "m.self" else "m.pin")) }
                )
                put("org.matrix.msc3488.ts", JsonPrimitive(System.currentTimeMillis()))
                put("org.matrix.msc1767.text", JsonPrimitive(body))
            }
        outbox.sendMessage(roomId, messageParams(roomId, "", replyTo, baseContent = content))
    }

    suspend fun resend(localId: String) = outbox.resend(localId)

    suspend fun discard(localId: String) = outbox.discard(localId)

    /**
     * Runs a structured command (MSC4391): a message addressed to the command's source — gomuks
     * itself for its built-ins, else the bot — carrying typed [arguments]. [typed] is what the user
     * wrote, kept as the body so other clients show something readable.
     */
    suspend fun sendCommand(
        command: BotCommand,
        arguments: JsonObject,
        typed: String,
    ) {
        outbox.sendMessage(
            roomId,
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("text", JsonPrimitive(""))
                put(
                    "base_content",
                    buildJsonObject {
                        put("msgtype", JsonPrimitive("m.text"))
                        put("body", JsonPrimitive(typed))
                        put(
                            COMMAND_KEY,
                            buildJsonObject {
                                put("command", JsonPrimitive(command.command))
                                put("arguments", arguments)
                            },
                        )
                    },
                )
                put(
                    "mentions",
                    buildJsonObject {
                        put("user_ids", JsonArray(listOf(JsonPrimitive(command.source))))
                        put("room", JsonPrimitive(false))
                    },
                )
            },
        )
    }

    /**
     * Reacts to [target] with [key] — an emoji, or an `mxc://` URI for a custom one (then with its
     * [shortcode], like gomuks web) — through the outbox.
     */
    suspend fun react(
        target: String,
        key: String,
        shortcode: String? = null,
    ) {
        val content =
            buildJsonObject {
                put(
                    "m.relates_to",
                    buildJsonObject {
                        put("rel_type", JsonPrimitive("m.annotation"))
                        put("event_id", JsonPrimitive(target))
                        put("key", JsonPrimitive(key))
                    },
                )
                if (shortcode != null) put("com.beeper.reaction.shortcode", JsonPrimitive(":$shortcode:"))
            }
        outbox.enqueue(
            roomId,
            "send_event",
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive("m.reaction"))
                put("content", content)
            },
        )
    }

    /** Sends a sticker from a pack (gomuks turns an m.sticker msgtype into an m.sticker event). */
    suspend fun sendSticker(image: PackImage) {
        outbox.sendMessage(
            roomId,
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("text", JsonPrimitive(""))
                put(
                    "base_content",
                    buildJsonObject {
                        put("msgtype", JsonPrimitive("m.sticker"))
                        put("body", JsonPrimitive(image.body))
                        put("url", JsonPrimitive(image.mxc))
                        put("info", image.info ?: JsonObject(emptyMap()))
                    },
                )
            },
        )
    }

    /** Sets account data ([room] null = global); last write wins, so no outbox needed. */
    suspend fun setAccountData(
        type: String,
        content: JsonObject,
        room: Boolean = false,
    ) {
        val params =
            buildJsonObject {
                if (room) put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(type))
                put("content", content)
            }
        exec.exec("set_account_data", params, ExecMode.Write)
    }

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

private const val COMMAND_KEY = "org.matrix.msc4391.command"

/**
 * `send_message`'s parameters: [text] (markdown, or a caption when there's [baseContent]), an edit
 * of [editing], or a reply to [replyTo] — which, like gomuks web, pings who wrote the original.
 */
internal fun messageParams(
    roomId: String,
    text: String,
    replyTo: ReplyTarget? = null,
    editing: String? = null,
    baseContent: JsonObject? = null,
): JsonObject =
    buildJsonObject {
        put("room_id", JsonPrimitive(roomId))
        put("text", JsonPrimitive(text))
        baseContent?.let { put("base_content", it) }
        when {
            editing != null -> {
                put(
                    "relates_to",
                    buildJsonObject {
                        put("rel_type", JsonPrimitive("m.replace"))
                        put("event_id", JsonPrimitive(editing))
                    },
                )
            }

            replyTo != null -> {
                val inReplyTo = buildJsonObject { put("event_id", JsonPrimitive(replyTo.eventId)) }
                put("relates_to", buildJsonObject { put("m.in_reply_to", inReplyTo) })
                put("mentions", buildJsonObject { put("user_ids", JsonArray(listOf(JsonPrimitive(replyTo.sender)))) })
            }
        }
    }
