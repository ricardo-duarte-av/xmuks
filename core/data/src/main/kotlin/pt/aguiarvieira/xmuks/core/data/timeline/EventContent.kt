package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

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
): String {
    val senderName = members[sender]?.displayName?.takeIf { it.isNotBlank() } ?: localpart(sender)
    val shown = perMessage?.str("displayname")?.takeIf { it.isNotBlank() } ?: return senderName
    return "$shown via $senderName"
}

internal fun localpart(userId: String): String = userId.removePrefix("@").substringBefore(':')

internal fun JsonObject.obj(key: String) = get(key) as? JsonObject

internal fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
