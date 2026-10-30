package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.protocol.Event

/** A room's events as their JSON, for viewing their source. */
class EventSources(
    private val roomId: String,
    private val events: EventCache,
) {
    /** [eventId]'s source (see [eventSource]); null when gomuks can't answer. */
    suspend fun json(eventId: String): String? = events.fetch(roomId, eventId)?.let(::eventSource)
}

/**
 * [event] as Matrix has it, pretty-printed: an encrypted one as it decrypted (its cleartext type
 * and content), without gomuks' own bookkeeping (row ids, local content, aggregated reactions).
 */
fun eventSource(event: Event): String {
    val json =
        buildJsonObject {
            put("type", JsonPrimitive(event.effectiveType))
            put("sender", JsonPrimitive(event.sender))
            put("event_id", JsonPrimitive(event.eventId))
            put("room_id", JsonPrimitive(event.roomId))
            put("origin_server_ts", JsonPrimitive(event.timestamp))
            event.stateKey?.let { put("state_key", JsonPrimitive(it)) }
            put("content", event.effectiveContent)
            event.unsigned?.takeIf { it.isNotEmpty() }?.let { put("unsigned", it) }
        }
    return PRETTY.encodeToString(JsonElement.serializer(), json)
}

private val PRETTY =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
