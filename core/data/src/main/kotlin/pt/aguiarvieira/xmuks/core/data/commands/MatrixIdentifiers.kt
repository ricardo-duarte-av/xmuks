package pt.aguiarvieira.xmuks.core.data.commands

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.net.URLDecoder

/**
 * Matrix identifiers as command arguments: a bare ID, a matrix.to link, a `matrix:` URI, or a
 * markdown link to one. Rooms and events become `{type, id, event_id?, via?}` objects (MSC4391).
 */
internal object MatrixIdentifiers {
    fun parse(
        type: String,
        raw: String,
    ): JsonElement? {
        if (raw.isEmpty()) return null
        val value = MARKDOWN_LINK.matchEntire(raw)?.groupValues?.get(1) ?: raw
        val link = MatrixLink.parse(value) ?: MatrixLink(value, eventId = null, via = emptyList())
        return when (type) {
            "user_id" -> link.id.takeIf { it.startsWith("@") && it.contains(':') }?.let(::JsonPrimitive)
            "room_alias" -> link.id.takeIf { it.startsWith("#") && it.contains(':') }?.let(::JsonPrimitive)
            "room_id" -> roomReference(link, withEvent = false)
            "event_id" -> roomReference(link, withEvent = true)
            else -> null
        }
    }

    private fun roomReference(
        link: MatrixLink,
        withEvent: Boolean,
    ): JsonElement? {
        if (!link.id.startsWith("!")) return null
        val eventId = link.eventId?.takeIf { it.startsWith("$") }
        if (withEvent && eventId == null) return null
        return buildJsonObject {
            put("type", JsonPrimitive(if (withEvent) "event_id" else "room_id"))
            put("id", JsonPrimitive(link.id))
            if (withEvent) put("event_id", JsonPrimitive(eventId))
            if (link.via.isNotEmpty()) put("via", buildJsonArray { link.via.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private val MARKDOWN_LINK = Regex("""^\[.+]\(([^)]+)\)$""")
}

/** A `matrix:` URI or matrix.to link: the ID it points at, an event in it, and via servers. */
data class MatrixLink(
    val id: String,
    val eventId: String?,
    val via: List<String>,
) {
    companion object {
        private val SIGILS = mapOf("u" to "@", "roomid" to "!", "r" to "#", "e" to "$")

        fun parse(value: String): MatrixLink? =
            when {
                value.startsWith("https://matrix.to/#/") -> parseMatrixTo(value.removePrefix("https://matrix.to/#/"))
                value.startsWith("matrix:") -> parseMatrixUri(value.removePrefix("matrix:"))
                else -> null
            }

        private fun parseMatrixTo(rest: String): MatrixLink? {
            val (path, query) = rest.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            val parts = path.split('/').map(::decode)
            val id = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            return MatrixLink(id, parts.getOrNull(1)?.takeIf { it.startsWith("$") }, via(query))
        }

        private fun parseMatrixUri(rest: String): MatrixLink? {
            val (path, query) = rest.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            val parts = path.split('/')
            val sigil = SIGILS[parts[0]]?.takeIf { parts.size >= 2 } ?: return null
            val eventId = if (parts.size >= EVENT_PARTS && parts[2] == "e") "$" + decode(parts[3]) else null
            return MatrixLink(sigil + decode(parts[1]), eventId, via(query))
        }

        private fun via(query: String?): List<String> =
            query
                ?.split('&')
                ?.mapNotNull {
                    it
                        .split('=', limit = 2)
                        .takeIf { kv ->
                            kv.size == 2 && kv[0] == "via"
                        }?.get(1)
                        ?.let(::decode)
                }.orEmpty()

        private fun decode(value: String) = URLDecoder.decode(value, "UTF-8") // the Charset overload is API 33+

        /** `roomid/<id>/e/<event>` */
        private const val EVENT_PARTS = 4
    }
}
