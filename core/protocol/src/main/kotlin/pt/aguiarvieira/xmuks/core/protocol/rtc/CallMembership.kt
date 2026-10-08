package pt.aguiarvieira.xmuks.core.protocol.rtc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import pt.aguiarvieira.xmuks.core.protocol.Event
import java.security.MessageDigest
import java.util.Base64

// MatrixRTC (MSC4143) call membership, in the two forms Element Call and matrix-js-sdk use
// (matrix-js-sdk src/matrixrtc/CallMembership.ts, membershipData/{session,rtc}.ts):
//  - Legacy: state event `org.matrix.msc3401.call.member`, one per device, left by sending `{}` —
//    normally by a delayed event (MSC4140) the client keeps restarting while it's alive.
//  - Sticky: MSC4354 sticky event `org.matrix.msc4143.rtc.member`, keyed by a per-join member id,
//    gone when its sticky duration (1 h, renewed) runs out or a content-less one replaces it.

/** Which of the two membership encodings an event (or our own join) uses. */
enum class MembershipFormat { Legacy, Sticky }

/** A MatrixRTC transport (MSC4143 focus), e.g. `{type: livekit, livekit_service_url}`. */
data class RtcTransport(
    val raw: JsonObject,
) {
    val type: String? get() = raw.string("type")

    /** The LiveKit JWT service ("lk-jwt-service") base URL, for `type == livekit`. */
    val livekitServiceUrl: String? get() = raw.string("livekit_service_url")

    companion object {
        fun livekit(serviceUrl: String) =
            RtcTransport(
                buildJsonObject {
                    put("type", RtcTypes.TRANSPORT_LIVEKIT)
                    put("livekit_service_url", serviceUrl)
                },
            )
    }
}

/**
 * One device's presence in a room's call, parsed from either encoding.
 *
 * [key] identifies the membership slot that later events replace: the state key (legacy) or the
 * sticky key (sticky). [rtcIdentity] is the participant identity on the SFU.
 */
data class CallMembership(
    val format: MembershipFormat,
    val roomId: String,
    val eventId: String,
    val key: String,
    val userId: String,
    val deviceId: String,
    val memberId: String,
    val slotId: String,
    val application: String,
    /** `m.call.intent`: "audio", "video", or anything else a client invents. */
    val intent: String?,
    val transports: List<RtcTransport>,
    /** When this device joined (legacy `created_ts`, else the event's own timestamp). */
    val createdTs: Long,
    /** When the membership stops counting, even without a leave: legacy `expires`, sticky duration. */
    val expiresAt: Long,
    val rtcIdentity: String,
) {
    /** The SFU this member publishes on (multi-SFU: everyone their own). */
    val transport: RtcTransport? get() = transports.firstOrNull()

    val isVideo: Boolean get() = intent == RtcTypes.INTENT_VIDEO

    fun isExpired(now: Long): Boolean = now >= expiresAt
}

object CallMemberships {
    /** Legacy memberships last this long after `created_ts` unless `expires` says otherwise. */
    const val DEFAULT_LEGACY_EXPIRY_MS = 4 * 60 * 60 * 1000L

    /** How long sticky memberships stay sticky; refreshed shortly before it runs out. */
    const val STICKY_DURATION_MS = 60 * 60 * 1000L

    /** The room-wide call every client uses today. */
    const val ROOM_SLOT_ID = "m.call#ROOM"

    /**
     * The membership an event carries, or null when it's a leave, malformed, or not a membership.
     * A sticky event without [Event.stickyDurationMs] (e.g. fetched outside sync) is given the
     * default sticky duration.
     */
    fun parse(event: Event): CallMembership? =
        when (event.effectiveType) {
            RtcTypes.LEGACY_MEMBER -> parseLegacy(event)
            RtcTypes.STICKY_MEMBER -> parseSticky(event)
            else -> null
        }

    /** True for a membership event that means "this device left" (empty or content-less). */
    fun isLeave(event: Event): Boolean =
        when (event.effectiveType) {
            RtcTypes.LEGACY_MEMBER -> event.effectiveContent.string("device_id") == null
            RtcTypes.STICKY_MEMBER -> event.effectiveContent["member"] !is JsonObject
            else -> false
        }

    /** The key a membership event occupies (state key / sticky key), for leaves as well as joins. */
    fun keyOf(event: Event): String? =
        when (event.effectiveType) {
            RtcTypes.LEGACY_MEMBER -> event.stateKey
            RtcTypes.STICKY_MEMBER -> stickyKey(event.effectiveContent)
            else -> null
        }

    private fun parseLegacy(event: Event): CallMembership? {
        val c = event.effectiveContent
        val deviceId = c.string("device_id") ?: return null
        val callId = c.string("call_id") ?: return null
        val application = c.string("application") ?: return null
        (c["focus_active"] as? JsonObject)?.string("type") ?: return null
        val created = (c["created_ts"] as? JsonPrimitive)?.longOrNull ?: event.timestamp
        val expires = (c["expires"] as? JsonPrimitive)?.longOrNull ?: DEFAULT_LEGACY_EXPIRY_MS
        val slot = if (application == RtcTypes.APPLICATION_CALL && callId.isEmpty()) "ROOM" else callId
        return CallMembership(
            format = MembershipFormat.Legacy,
            roomId = event.roomId,
            eventId = event.eventId,
            key = event.stateKey ?: return null,
            userId = event.sender,
            deviceId = deviceId,
            memberId = c.string("membershipID") ?: "${event.sender}:$deviceId",
            slotId = "$application#$slot",
            application = application,
            intent = c.string(RtcTypes.INTENT_KEY),
            transports = c.transportList("foci_preferred"),
            createdTs = created,
            expiresAt = created + expires,
            rtcIdentity = legacyIdentity(event.sender, deviceId),
        )
    }

    private fun parseSticky(event: Event): CallMembership? {
        val c = event.effectiveContent
        val app = c["application"] as? JsonObject ?: return null
        val type = app.string("type")?.takeIf { '#' !in it } ?: return null
        val slotId = c.string("slot_id")?.takeIf { it.startsWith("$type#") && it.count { ch -> ch == '#' } == 1 }
        val member = c["member"] as? JsonObject ?: return null
        // Only the member themself may say they're in the call.
        val userId = member.string("user_id")?.takeIf { it == event.sender } ?: return null
        val deviceId = member.string("device_id") ?: return null
        val memberId = member.string("id") ?: return null
        val key = stickyKey(c) ?: return null
        val published = (c["transports"] as? JsonObject)?.transportList("published") ?: return null
        if (slotId == null) return null
        return CallMembership(
            format = MembershipFormat.Sticky,
            roomId = event.roomId,
            eventId = event.eventId,
            key = key,
            userId = userId,
            deviceId = deviceId,
            memberId = memberId,
            slotId = slotId,
            application = type,
            intent = app.string(RtcTypes.INTENT_KEY),
            transports = published,
            createdTs = event.timestamp,
            expiresAt = event.timestamp + (event.stickyDurationMs ?: STICKY_DURATION_MS),
            rtcIdentity = stickyIdentity(userId, deviceId, memberId),
        )
    }

    private fun stickyKey(content: JsonObject): String? =
        content.string("sticky_key") ?: content.string("msc4354_sticky_key")

    /** SFU identity of a legacy member: what lk-jwt-service's `/sfu/get` assigns. */
    fun legacyIdentity(
        userId: String,
        deviceId: String,
    ) = "$userId:$deviceId"

    /** SFU identity of a sticky member: unpadded base64 of SHA-256 over `["user","device","member"]`. */
    fun stickyIdentity(
        userId: String,
        deviceId: String,
        memberId: String,
    ): String {
        val json = JsonArray(listOf(JsonPrimitive(userId), JsonPrimitive(deviceId), JsonPrimitive(memberId))).toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** State key of our own legacy membership (rooms of MSC3757/3779 versions drop the `_`). */
    fun legacyStateKey(
        userId: String,
        deviceId: String,
        roomVersion: String? = null,
    ): String {
        val key = "${userId}_${deviceId}_${RtcTypes.APPLICATION_CALL}"
        return if (roomVersion != null && OWNED_STATE_VERSIONS.containsMatchIn(roomVersion)) key else "_$key"
    }

    private val OWNED_STATE_VERSIONS = Regex("^org\\.matrix\\.msc(3757|3779)\\b")

    /** Our own legacy membership content, as matrix-js-sdk's `makeMyMembership` builds it. */
    fun legacyContent(
        userId: String,
        deviceId: String,
        intent: String,
        transports: List<RtcTransport>,
        expiresMs: Long,
        createdTs: Long?,
    ): JsonObject =
        buildJsonObject {
            put("application", RtcTypes.APPLICATION_CALL)
            put("call_id", "")
            put("scope", "m.room")
            put("device_id", deviceId)
            put("membershipID", legacyIdentity(userId, deviceId))
            put("expires", expiresMs)
            put(RtcTypes.INTENT_KEY, intent)
            put(
                "focus_active",
                buildJsonObject {
                    put("type", RtcTypes.TRANSPORT_LIVEKIT)
                    put("focus_selection", "multi_sfu")
                },
            )
            put("foci_preferred", JsonArray(transports.map { it.raw }))
            if (createdTs != null) put("created_ts", createdTs)
        }

    /** Our own sticky membership content (`StickyEventMembershipManager.makeMyMembership`). */
    fun stickyContent(
        userId: String,
        deviceId: String,
        memberId: String,
        intent: String,
        transports: List<RtcTransport>,
    ): JsonObject =
        buildJsonObject {
            put("slot_id", ROOM_SLOT_ID)
            put(
                "application",
                buildJsonObject {
                    put("type", RtcTypes.APPLICATION_CALL)
                    put(RtcTypes.INTENT_KEY, intent)
                },
            )
            put(
                "transports",
                buildJsonObject {
                    put("published", JsonArray(transports.map { it.raw }))
                    put("can_subscribe", buildJsonArray { add(JsonPrimitive(RtcTypes.TRANSPORT_LIVEKIT)) })
                },
            )
            put(
                "member",
                buildJsonObject {
                    put("device_id", deviceId)
                    put("user_id", userId)
                    put("id", memberId)
                },
            )
            put("versions", JsonArray(emptyList()))
            put("msc4354_sticky_key", memberId)
        }

    /** What a sticky leave (and the delayed leave scheduled at join) carries. */
    fun stickyLeaveContent(memberId: String): JsonObject =
        buildJsonObject {
            put("slot_id", ROOM_SLOT_ID)
            put("msc4354_sticky_key", memberId)
        }

    private fun JsonObject.transportList(name: String): List<RtcTransport> =
        (this[name] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.takeIf { t -> t.string("type") != null }?.let(::RtcTransport) }
            .orEmpty()
}

internal fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeIf {
            it.isString
        }?.contentOrNull
