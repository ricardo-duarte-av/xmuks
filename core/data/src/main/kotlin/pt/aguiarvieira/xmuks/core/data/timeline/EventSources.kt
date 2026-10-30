package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult

/** A room's events as gomuks has them, for viewing their source. */
class EventSources(
    private val roomId: String,
    private val exec: ExecClient,
) {
    /** [eventId]'s source (see [eventSource]); null when gomuks can't answer. */
    suspend fun json(eventId: String): String? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
            }
        val result = exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        return (result.data as? JsonObject)?.let(::eventSource)
    }
}

/**
 * gomuks' whole row for an event, pretty-printed, as its web client shows it: an encrypted one
 * with its cleartext type and content in place, and what was sent under `encrypted`.
 */
fun eventSource(row: JsonObject): String {
    val decrypted = row[DECRYPTED] as? JsonObject
    val json =
        if (decrypted == null) {
            row
        } else {
            buildJsonObject {
                row.forEach { (key, value) ->
                    when (key) {
                        DECRYPTED, DECRYPTED_TYPE -> Unit
                        TYPE -> put(key, row[DECRYPTED_TYPE] ?: value)
                        CONTENT -> put(key, decrypted)
                        else -> put(key, value)
                    }
                }
                row[CONTENT]?.let { put(ENCRYPTED, it) }
            }
        }
    return PRETTY.encodeToString(JsonElement.serializer(), json)
}

private const val TYPE = "type"
private const val CONTENT = "content"
private const val DECRYPTED = "decrypted"
private const val DECRYPTED_TYPE = "decrypted_type"
private const val ENCRYPTED = "encrypted"

private val PRETTY =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
