package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
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
fun bridgeOf(state: List<Event>): BridgeInfo? =
    state
        .filter { it.effectiveType in BRIDGE_TYPES && it.redactedBy == null }
        .firstNotNullOfOrNull { event ->
            val content = event.effectiveContent
            val protocol = content.obj("protocol") ?: return@firstNotNullOfOrNull null
            val channel = content.obj("channel")
            BridgeInfo(
                protocol = protocol.str("displayname") ?: protocol.str("id") ?: return@firstNotNullOfOrNull null,
                protocolAvatarMxc = protocol.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
                channel = channel?.str("displayname"),
                channelAvatarMxc = channel?.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
                bot = content.str("bridgebot"),
            )
        }

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
