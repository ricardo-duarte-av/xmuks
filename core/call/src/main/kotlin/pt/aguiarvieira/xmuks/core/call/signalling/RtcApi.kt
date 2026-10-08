package pt.aguiarvieira.xmuks.core.call.signalling

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import java.io.IOException

/** gomuks' RPC commands for MatrixRTC (spec.mau.fi/gomuks/rpc.html), typed. */
class RtcApi(
    private val exec: ExecClient,
) {
    /** MSC4143 transports the homeserver advertises (`get_rtc_transports`). */
    suspend fun transports(): Result<List<RtcTransport>> =
        exec.exec("get_rtc_transports", mode = ExecMode.Read).toResult().mapCatching { data ->
            ((data as JsonObject)["rtc_transports"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.let(::RtcTransport) }
                .orEmpty()
        }

    /** The homeserver's `/versions` `unstable_features`. */
    suspend fun unstableFeatures(): Result<Map<String, Boolean>> =
        exec.exec("get_versions", mode = ExecMode.Read).toResult().mapCatching { data ->
            ((data as JsonObject)["unstable_features"] as? JsonObject)
                ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.booleanOrNull == true }
                .orEmpty()
        }

    /** An OpenID token proving our Matrix identity to a third party (the LiveKit JWT service). */
    suspend fun openIdToken(): Result<JsonObject> =
        exec.exec("request_openid_token", mode = ExecMode.Read).toResult().mapCatching { it as JsonObject }

    /**
     * A LiveKit JWT through the homeserver's MSC4195 endpoint (`rtc_livekit_get_token`). Fails when
     * gomuks or the homeserver doesn't have it; callers fall back to asking the JWT service directly.
     */
    suspend fun livekitToken(
        serviceUrl: String,
        roomId: String,
        slotId: String,
        memberId: String,
    ): Result<String> {
        val params =
            buildJsonObject {
                put("url", serviceUrl)
                put("room_id", roomId)
                put("slot_id", slotId)
                put("member_id", memberId)
            }
        return exec.exec("rtc_livekit_get_token", params, ExecMode.Read).toResult().mapCatching {
            ((it as JsonObject)["jwt"] as JsonPrimitive).content
        }
    }

    /** Sends state; with [delayMs], schedules it instead and returns the delay id. */
    suspend fun setState(
        roomId: String,
        type: String,
        stateKey: String,
        content: JsonObject,
        delayMs: Long? = null,
    ): Result<String> {
        val params =
            buildJsonObject {
                put("room_id", roomId)
                put("type", type)
                put("state_key", stateKey)
                put("content", content)
                if (delayMs != null) put("delay_ms", delayMs)
            }
        return exec.exec("set_state", params, ExecMode.Write).toResult().mapCatching(::idOf)
    }

    /** Sends an MSC4354 sticky event (never encrypted by gomuks); with [delayMs], schedules it. */
    suspend fun sendSticky(
        roomId: String,
        type: String,
        content: JsonObject,
        stickyDurationMs: Long,
        delayMs: Long? = null,
    ): Result<String> {
        val params =
            buildJsonObject {
                put("room_id", roomId)
                put("type", type)
                put("content", content)
                put("sticky_duration_ms", stickyDurationMs)
                if (delayMs != null) put("delay_ms", delayMs)
            }
        return exec.exec("send_sticky_event", params, ExecMode.Write).toResult().mapCatching(::idOf)
    }

    /** Sends a room event and waits until the homeserver has it; returns its event id. */
    suspend fun sendEvent(
        roomId: String,
        type: String,
        content: JsonObject,
    ): Result<String> {
        val params =
            buildJsonObject {
                put("room_id", roomId)
                put("type", type)
                put("content", content)
                put("synchronous", true)
            }
        return exec.exec("send_event", params, ExecMode.Write).toResult().mapCatching { data ->
            ((data as JsonObject)["event_id"] as JsonPrimitive).content
        }
    }

    suspend fun redact(
        roomId: String,
        eventId: String,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("room_id", roomId)
                put("event_id", eventId)
            }
        return exec.exec("redact_event", params, ExecMode.Write).toResult().map { }
    }

    /** MSC4140: restart, cancel or send a scheduled event now. */
    suspend fun updateDelayed(
        delayId: String,
        action: DelayAction,
    ): ExecResult {
        val params =
            buildJsonObject {
                put("delay_id", delayId)
                put("action", action.wire)
            }
        return exec.exec("update_delayed_event", params, ExecMode.Read)
    }

    /** To-device messages, Olm-encrypted per device when [encrypted]. */
    suspend fun sendToDevice(
        type: String,
        messages: Map<String, Map<String, JsonObject>>,
        encrypted: Boolean,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("event_type", type)
                put("encrypted", encrypted)
                put("messages", JsonObject(messages.mapValues { (_, devices) -> JsonObject(devices) }))
            }
        return exec.exec("send_to_device", params, ExecMode.Write).toResult().map { }
    }

    /** Includes (or stops including) to-device events in `sync_complete`. Global to the backend. */
    suspend fun listenToDevice(listen: Boolean): Result<Boolean> =
        exec.exec("listen_to_device", JsonPrimitive(listen), ExecMode.Read).toResult().mapCatching {
            (it as? JsonPrimitive)?.booleanOrNull == true
        }

    /** The room's currently sticky events, from gomuks' database. */
    suspend fun stickyEvents(roomId: String): Result<List<Event>> =
        exec
            .exec(
                "get_sticky_events",
                buildJsonObject {
                    put("room_id", roomId)
                },
                ExecMode.Read
            ).toResult()
            .mapCatching(::events)

    /** The room's current state (members excluded). */
    suspend fun roomState(roomId: String): Result<List<Event>> {
        val params =
            buildJsonObject {
                put("room_id", roomId)
                put("include_members", false)
                put("fetch_members", false)
                put("refetch", false)
            }
        return exec.exec("get_room_state", params, ExecMode.Read).toResult().mapCatching(::events)
    }

    private fun events(data: JsonElement): List<Event> =
        if (data is JsonArray) {
            GomuksJson.decodeFromJsonElement(
                ListSerializer(Event.serializer()),
                data
            )
        } else {
            emptyList()
        }

    /** `set_state` / `send_sticky_event` answer with a bare string: the event id or the delay id. */
    private fun idOf(data: JsonElement): String =
        (data as? JsonPrimitive)?.contentOrNull
            ?: ((data as? JsonObject)?.get("delay_id") ?: (data as? JsonObject)?.get("event_id"))
                ?.let { (it as JsonPrimitive).content }
            ?: error("No id in $data")
}

enum class DelayAction(
    val wire: String,
) {
    Restart("restart"),
    Cancel("cancel"),
    Send("send"),
}

/** A command gomuks ran and that failed, with the homeserver's `errcode` when there was one. */
class RtcCommandException(
    val errcode: String?,
    message: String,
) : IOException(message)

internal fun ExecResult.toResult(): Result<JsonElement> =
    when (this) {
        is ExecResult.Ok -> Result.success(data)
        is ExecResult.CommandError -> Result.failure(RtcCommandException(errcode, message))
        is ExecResult.NetworkError -> Result.failure(cause)
    }
