package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The lenient JSON setup shared by every gomuks payload: unknown fields are expected and ignored. */
val GomuksJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

@Serializable
private class Container<T>(
    val command: String,
    @SerialName("request_id") val requestId: Long = 0,
    val data: T,
)

/**
 * Decodes `/sse` lines (the `application/jsonl` format: one JSON object per line).
 *
 * gomuks writes `command` as the first key, so the command is read off the line prefix and the whole
 * line is then decoded straight into its typed container — one pass, no intermediate JSON tree for
 * the large `sync_complete` lines.
 */
object FrameDecoder {
    private const val PREFIX = "{\"command\":\""

    /** Returns null for keepalive pings (`null`) and blank lines. */
    fun decode(line: String): GomuksFrame? {
        if (line.isBlank() || line == "null") return null
        return when (val command = commandOf(line)) {
            "sync_complete" -> typed(line, SyncComplete.serializer()) { GomuksEvent.Sync(it) }
            "run_id" -> typed(line, GomuksEvent.RunId.serializer()) { it }
            "client_state" -> typed(line, GomuksEvent.ClientState.serializer()) { it }
            "sync_status" -> typed(line, GomuksEvent.SyncStatus.serializer()) { it }
            "image_auth_token" -> typed(line, String.serializer()) { GomuksEvent.ImageAuthToken(it) }
            "init_complete" -> typed(line, JsonElement.serializer()) { GomuksEvent.InitComplete }
            "events_decrypted" -> typed(line, GomuksEvent.EventsDecrypted.serializer()) { it }
            "typing" -> typed(line, GomuksEvent.Typing.serializer()) { it }
            "send_complete" -> typed(line, GomuksEvent.SendComplete.serializer()) { it }
            else -> generic(line, command)
        }
    }

    private fun commandOf(line: String): String? {
        if (!line.startsWith(PREFIX)) return null
        val end = line.indexOf('"', PREFIX.length)
        return if (end > 0) line.substring(PREFIX.length, end) else null
    }

    private inline fun <T> typed(
        line: String,
        serializer: KSerializer<T>,
        wrap: (T) -> GomuksEvent,
    ): GomuksFrame {
        val container = GomuksJson.decodeFromString(Container.serializer(serializer), line)
        return GomuksFrame(container.command, container.requestId, wrap(container.data))
    }

    /** Fallback for unknown commands, or a line whose key order doesn't match the fast path. */
    private fun generic(
        line: String,
        knownCommand: String?,
    ): GomuksFrame? {
        val obj = GomuksJson.parseToJsonElement(line).jsonObject
        val command = knownCommand ?: obj["command"]?.jsonPrimitive?.content ?: return null
        val requestId = (obj["request_id"] as? JsonPrimitive)?.longOrNull ?: 0
        if (knownCommand == null && command in TYPED) return decode(reorder(command, requestId, obj["data"]))
        return GomuksFrame(command, requestId, GomuksEvent.Unknown(command, obj["data"]?.takeIf { it != JsonNull }))
    }

    private val TYPED =
        setOf(
            "sync_complete",
            "run_id",
            "client_state",
            "sync_status",
            "image_auth_token",
            "init_complete",
            "events_decrypted",
            "typing",
            "send_complete",
        )

    private fun reorder(
        command: String,
        requestId: Long,
        data: JsonElement?,
    ) = "$PREFIX$command\",\"request_id\":$requestId,\"data\":${data ?: JsonNull}}"
}
