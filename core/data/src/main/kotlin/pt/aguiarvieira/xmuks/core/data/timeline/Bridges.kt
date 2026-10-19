package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.Receipt

/** What a bridged room is bridged to (its `m.bridge` state): the network, and the room there. */
data class BridgeInfo(
    val protocol: String,
    val protocolAvatarMxc: String?,
    val channel: String?,
    val channelAvatarMxc: String?,
    /** The bridge's bot, which also reports how our messages fared there. */
    val bot: String?,
    /** The network's ID ("whatsapp"): what its bridged members name as theirs. */
    val protocolId: String? = null,
)

/** How one of our messages fared on the bridged network, from the bridge's status reports. */
enum class BridgeDelivery {
    /** The network took it. */
    Sent,

    /** It reached the person on the other side. */
    Delivered,

    /** The bridge couldn't get it there. */
    Failed,
}

private val BRIDGE_TYPES = setOf("m.bridge", "uk.half-shot.bridge")
internal const val SEND_STATUS = "com.beeper.message_send_status"

/** The bridge described in a room's [state], if any. */
fun bridgeOf(state: List<Event>): BridgeInfo? = bridgesOf(state).firstOrNull()

/** Every bridge described in a room's [state]. */
private fun bridgesOf(state: List<Event>): List<BridgeInfo> =
    state
        .filter { it.effectiveType in BRIDGE_TYPES && it.redactedBy == null }
        .mapNotNull { event ->
            val content = event.effectiveContent
            val protocol = content.obj("protocol") ?: return@mapNotNull null
            val channel = content.obj("channel")
            BridgeInfo(
                protocol = protocol.str("displayname") ?: protocol.str("id") ?: return@mapNotNull null,
                protocolAvatarMxc = protocol.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
                channel = channel?.str("displayname"),
                channelAvatarMxc = channel?.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
                bot = content.str("bridgebot"),
                protocolId = protocol.str("id"),
            )
        }

/** The networks the room's bridges reach, by their protocol ID (what bridged members name). */
fun networksOf(state: List<Event>): Map<String, SenderNetwork> =
    bridgesOf(state)
        .mapNotNull { bridge ->
            bridge.protocolId?.let { it to SenderNetwork(bridge.protocol, bridge.protocolAvatarMxc) }
        }.toMap()

/**
 * A bridged sender's network among [networks]: the bridge with their protocol ID, else the room's
 * only bridge (its `m.bridge` may name the network differently). None for a message sent as a
 * [perMessageProfile]: that speaks for someone else, whose network the sender's isn't.
 */
internal fun senderNetworkOf(
    profile: MemberProfile?,
    perMessageProfile: JsonObject?,
    networks: Map<String, SenderNetwork>,
): SenderNetwork? {
    if (perMessageProfile != null) return null
    return profile?.network?.let { networks[it] ?: networks.values.singleOrNull() }
}

/**
 * The network a member is a bridged user (puppet) of, from what mautrix bridges put in their
 * `m.room.member`: `com.beeper.bridge.network` (or `.service`), a protocol ID like "whatsapp".
 */
internal fun bridgeNetworkOf(member: JsonObject): String? =
    member.str("com.beeper.bridge.network")?.takeIf { it.isNotBlank() }
        ?: member.str("com.beeper.bridge.service")?.takeIf { it.isNotBlank() }

/**
 * Where [related] (the events referencing one of our messages) say it got to: delivered once any
 * report names who received it, failed if the newest report says so, otherwise sent once any
 * report succeeded. Null when the bridge hasn't said anything.
 */
internal fun bridgeDeliveryOf(related: List<Event>): BridgeDelivery? {
    val reports = related.filter { it.effectiveType == SEND_STATUS }.sortedBy { it.timestamp }
    val latest = reports.lastOrNull() ?: return null
    val delivered = reports.any { (it.effectiveContent["delivered_to_users"] as? JsonArray)?.isNotEmpty() == true }
    return when {
        delivered -> BridgeDelivery.Delivered
        latest.effectiveContent.str("status")?.startsWith("FAIL") == true -> BridgeDelivery.Failed
        reports.any { it.effectiveContent.str("status") == "SUCCESS" } -> BridgeDelivery.Sent
        else -> null
    }
}

/**
 * Our messages the other side is known to have received, beyond what each one's own reports say:
 * a report naming who received one of our messages, or a read receipt from anyone else, confirms
 * that message and every earlier one. Empty in rooms whose bridge sends no reports (nothing to
 * tick there); the bridge's own receipts don't count.
 */
internal fun deliveredThrough(
    events: List<Event>,
    references: Map<String?, List<Event>>,
    receipts: Map<String, List<Receipt>>,
    me: String?,
): Set<String> {
    val reports = references.values.flatten().filter { it.effectiveType == SEND_STATUS }
    if (me == null || reports.isEmpty()) return emptySet()
    val bridges = reports.mapTo(HashSet()) { it.sender }
    val confirmed =
        events.indexOfLast { event ->
            val delivered =
                event.sender == me && bridgeDeliveryOf(references[event.eventId].orEmpty()) == BridgeDelivery.Delivered
            delivered || receipts[event.eventId].orEmpty().any { it.userId != me && it.userId !in bridges }
        }
    if (confirmed < 0) return emptySet()
    return events
        .subList(0, confirmed + 1)
        .filter { it.sender == me }
        .mapTo(HashSet()) { it.eventId }
}
