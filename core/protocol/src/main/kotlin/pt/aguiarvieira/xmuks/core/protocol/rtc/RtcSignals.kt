package pt.aguiarvieira.xmuks.core.protocol.rtc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import pt.aguiarvieira.xmuks.core.protocol.Event

/**
 * A received MSC4075 call notification: "ring" (DMs) or "notification" (a group call started).
 *
 * matrix-js-sdk additionally requires an open `rtc.slot` state event before it rings, but Element
 * Call never creates one; like Element Call we don't require it.
 */
data class RtcNotification(
    val eventId: String,
    val roomId: String,
    val sender: String,
    val slotId: String,
    val ring: Boolean,
    val intent: String?,
    val mentionsRoom: Boolean,
    val mentionedUsers: List<String>,
    /** The sender's membership event this notification belongs to, when it says. */
    val membershipEventId: String?,
    /** Absolute expiry per MSC4075: `lifetime` from `sender_ts` (or from the server's ts). */
    val expiresAt: Long,
) {
    val isVideo: Boolean get() = intent == RtcTypes.INTENT_VIDEO

    fun mentions(userId: String) = mentionsRoom || userId in mentionedUsers
}

object RtcSignals {
    const val MAX_LIFETIME_MS = 2 * 60 * 1000L
    const val MAX_SENDER_TS_AHEAD_MS = 20 * 1000L

    /** What Element Call rings for. */
    const val RING_LIFETIME_MS = 90 * 1000L

    /** A notification is sent sticky for a little longer than it rings. */
    const val NOTIFICATION_STICKY_EXTRA_MS = 20 * 1000L

    /** Declines stay sticky this long. */
    const val DECLINE_STICKY_MS = 2 * 60 * 1000L

    fun parseNotification(event: Event): RtcNotification? {
        if (event.effectiveType != RtcTypes.NOTIFICATION) return null
        val c = event.effectiveContent
        val senderTs = (c["sender_ts"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: return null
        val lifetime = (c["lifetime"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: return null
        val mentions = c["m.mentions"] as? JsonObject ?: return null
        val room = (mentions["room"] as? JsonPrimitive)?.booleanOrNull == true
        val users =
            (mentions["user_ids"] as? JsonArray)
                ?.mapNotNull {
                    (it as? JsonPrimitive)
                        ?.takeIf { p ->
                            p.isString
                        }?.content
                }.orEmpty()
        if (!room && users.isEmpty()) return null
        val relation = c["m.relates_to"] as? JsonObject
        val reference = relation?.takeIf { it.string("rel_type") == "m.reference" }?.string("event_id")
        val type = c.string("notification_type") ?: c.string("notify_type")
        val basis = if (senderTs - event.timestamp > MAX_SENDER_TS_AHEAD_MS) event.timestamp else senderTs
        return RtcNotification(
            eventId = event.eventId,
            roomId = event.roomId,
            sender = event.sender,
            slotId = c.string("slot_id") ?: CallMemberships.ROOM_SLOT_ID,
            ring = type == "ring",
            intent = c.string(RtcTypes.INTENT_KEY),
            mentionsRoom = room,
            mentionedUsers = users,
            membershipEventId = reference,
            expiresAt = basis + minOf(lifetime, MAX_LIFETIME_MS),
        )
    }

    /** The notification we send once our own membership is in the room. */
    fun notificationContent(
        ring: Boolean,
        intent: String,
        membershipEventId: String,
        senderTs: Long,
        lifetimeMs: Long = RING_LIFETIME_MS,
    ): JsonObject =
        buildJsonObject {
            put("slot_id", CallMemberships.ROOM_SLOT_ID)
            put(
                "m.mentions",
                buildJsonObject {
                    put("user_ids", JsonArray(emptyList()))
                    put("room", true)
                },
            )
            put("notification_type", if (ring) "ring" else "notification")
            put(
                "m.relates_to",
                buildJsonObject {
                    put("rel_type", "m.reference")
                    put("event_id", membershipEventId)
                },
            )
            put("sender_ts", senderTs)
            put("lifetime", lifetimeMs)
            put("msc4354_sticky_key", CallMemberships.ROOM_SLOT_ID)
            put(RtcTypes.INTENT_KEY, intent)
        }

    /** Declining a ring: references the notification (and is keyed by it when sticky). */
    fun declineContent(notificationEventId: String): JsonObject =
        buildJsonObject {
            put(
                "m.relates_to",
                buildJsonObject {
                    put("rel_type", "m.reference")
                    put("event_id", notificationEventId)
                },
            )
            put("msc4354_sticky_key", notificationEventId)
        }

    /** The notification a decline answers, or null when [event] isn't a decline. */
    fun declinedNotification(event: Event): String? =
        if (event.effectiveType != RtcTypes.DECLINE) {
            null
        } else {
            (event.effectiveContent["m.relates_to"] as? JsonObject)?.string("event_id")
        }
}

/** One received `io.element.call.encryption_keys` to-device payload. */
data class MediaKey(
    val sender: String,
    val roomId: String,
    val deviceId: String,
    val memberId: String,
    val index: Int,
    val keyBase64: String,
    val sentTs: Long?,
)

object MediaKeys {
    /** Keys are 16 random bytes; indices wrap at 256 (LiveKit's key ring size). */
    const val KEY_BYTES = 16
    const val INDEX_COUNT = 256

    fun parse(
        sender: String,
        content: JsonObject,
    ): MediaKey? {
        val roomId = content.string("room_id") ?: return null
        val keys = content["keys"] as? JsonObject ?: return null
        val key = keys.string("key")?.takeIf { it.isNotEmpty() } ?: return null
        val index = (keys["index"] as? JsonPrimitive)?.longOrNull?.toInt() ?: return null
        val member = content["member"] as? JsonObject ?: return null
        val deviceId = member.string("claimed_device_id") ?: return null
        return MediaKey(
            sender = sender,
            roomId = roomId,
            deviceId = deviceId,
            memberId = member.string("id") ?: "$sender:$deviceId",
            index = index,
            keyBase64 = key,
            sentTs = (content["sent_ts"] as? JsonPrimitive)?.longOrNull,
        )
    }

    fun content(
        roomId: String,
        deviceId: String,
        memberId: String,
        index: Int,
        keyBase64: String,
        sentTs: Long,
    ): JsonObject =
        buildJsonObject {
            put(
                "keys",
                buildJsonObject {
                    put("index", index)
                    put("key", keyBase64)
                },
            )
            put("room_id", roomId)
            put(
                "member",
                buildJsonObject {
                    put("claimed_device_id", deviceId)
                    put("id", memberId)
                },
            )
            put(
                "session",
                buildJsonObject {
                    put("call_id", "")
                    put("application", RtcTypes.APPLICATION_CALL)
                    put("scope", "m.room")
                },
            )
            put("sent_ts", sentTs)
        }
}
