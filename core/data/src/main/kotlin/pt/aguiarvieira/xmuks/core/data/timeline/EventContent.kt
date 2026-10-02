package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.LocalContent

private const val HTML_FORMAT = "org.matrix.custom.html"

/**
 * gomuks' sanitised HTML first (it also linkifies plain text), then the event's own
 * `formatted_body` (our renderer only understands a safe subset anyway), else plain body.
 */
internal fun htmlOf(
    sanitized: String?,
    content: JsonObject,
): String? =
    sanitized?.takeIf { it.isNotBlank() }
        ?: content.str("formatted_body")?.takeIf { content.str("format") == HTML_FORMAT && it.isNotBlank() }

/**
 * The sender's per-room name, or for a per-message profile (bridges, bots speaking for someone)
 * "<profile name> via <sender name>".
 */
internal fun senderLabel(
    perMessage: JsonObject?,
    sender: String,
    members: Map<String, MemberProfile>,
): SenderLabel {
    val senderName = members[sender]?.displayName?.takeIf { it.isNotBlank() } ?: localpart(sender)
    val shown = perMessage?.str("displayname")?.takeIf { it.isNotBlank() }
    return SenderLabel(
        sender,
        senderName,
        profileId = perMessage?.str("id")?.takeIf { shown != null },
        profileName = shown
    )
}

/** gomuks' aggregated reactions, most-used first, ours marked. */
internal fun reactionsOf(
    event: Event,
    myReactions: Map<String, Map<String, String>>,
): List<Reaction> {
    val mine = myReactions[event.eventId].orEmpty()
    return event.reactions
        .orEmpty()
        // gomuks keeps keys whose reactions were all redacted, at count 0.
        .filterValues { it > 0 }
        .map { (key, count) -> Reaction(key, count, key in mine, mine[key]) }
        .sortedByDescending { it.count }
}

/** Marks text whose HTML is gomuks' linkified plain text (its line breaks are literal). */
internal fun MessageContent.withPlainText(local: LocalContent?): MessageContent =
    if (this is MessageContent.Text && local?.wasPlaintext == true && !local.sanitizedHtml.isNullOrBlank()) {
        copy(plainText = true)
    } else {
        this
    }

/** join → join with a new name and/or avatar; null when nothing visible changed. */
internal fun profileChange(
    previous: JsonObject?,
    content: JsonObject,
): Change? {
    val nameChanged = previous?.str("displayname") != content.str("displayname")
    val avatarChanged = previous?.str("avatar_url") != content.str("avatar_url")
    if (!nameChanged && !avatarChanged) return null
    return Change.ProfileChanged(
        oldName = previous?.str("displayname"),
        newName = content.str("displayname"),
        oldAvatar = previous?.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
        newAvatar = content.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
        nameChanged = nameChanged,
        avatarChanged = avatarChanged,
    )
}

/**
 * gomuks' view of our own event: a local echo still sending (its ID is gomuks' `~transaction`
 * placeholder until the homeserver assigns one), failed (a real send_error), or done.
 */
internal fun Event.sendState(): SendState =
    when {
        !sendError.isNullOrBlank() && sendError != "not sent" -> SendState.Failed
        pending || eventId.startsWith("~") -> SendState.Sending
        else -> SendState.Sent
    }

/** Our own editable (text) messages: gomuks' edit source, else the body. Null for anything else. */
internal fun TimelineItemBuilder.editSourceOf(
    event: Event,
    local: LocalContent?,
    content: JsonObject,
): String? {
    if (event.sender != me || event.redactedBy != null) return null
    if (content.str("msgtype") !in EDITABLE_MSGTYPES) return null
    return local?.editSource ?: content.str("body")
}

private val EDITABLE_MSGTYPES = setOf("m.text", "m.notice", "m.emote")

internal fun localpart(userId: String): String = userId.removePrefix("@").substringBefore(':')

internal fun JsonObject.obj(key: String) = get(key) as? JsonObject

internal fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
