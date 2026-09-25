package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Everything the `/sse` stream can deliver, decoded. */
sealed interface GomuksEvent {
    /** First message of every connection: identifies this backend run and our SSE listener. */
    @Serializable
    data class RunId(
        @SerialName("run_id") val runId: String,
        @SerialName("listener_id") val listenerId: Long = 0,
    ) : GomuksEvent

    @Serializable
    data class ClientState(
        @SerialName("is_initialized") val isInitialized: Boolean = false,
        @SerialName("is_logged_in") val isLoggedIn: Boolean = false,
        @SerialName("is_verified") val isVerified: Boolean = false,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("device_id") val deviceId: String? = null,
        @SerialName("homeserver_url") val homeserverUrl: String? = null,
        val displayname: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    ) : GomuksEvent

    /** Health of gomuks' own /sync loop against the homeserver. */
    @Serializable
    data class SyncStatus(
        val type: String,
        val error: String? = null,
        @SerialName("error_count") val errorCount: Int = 0,
        @SerialName("last_sync") val lastSync: Long = 0,
    ) : GomuksEvent

    /** Token for `/_gomuks/media` URLs (`?image_auth=`), valid 1 h; re-sent every 30 min. */
    data class ImageAuthToken(
        val token: String,
    ) : GomuksEvent

    data class Sync(
        val sync: SyncComplete,
    ) : GomuksEvent

    /** All initial or resume data has been sent; everything after this is live. */
    data object InitComplete : GomuksEvent

    @Serializable
    data class EventsDecrypted(
        @SerialName("room_id") val roomId: String,
        @SerialName("preview_event_rowid") val previewEventRowId: Long = 0,
        @SerialName("sorting_timestamp") val sortingTimestamp: Long = 0,
        val events: List<Event> = emptyList(),
    ) : GomuksEvent

    @Serializable
    data class Typing(
        @SerialName("room_id") val roomId: String,
        @SerialName("user_ids") val userIds: List<String> = emptyList(),
    ) : GomuksEvent

    /** Outcome of a message send started over `/exec`. [error] is whatever gomuks serialised. */
    @Serializable
    data class SendComplete(
        val event: Event? = null,
        val error: JsonElement? = null,
    ) : GomuksEvent

    /** A command this client doesn't model (yet). Kept so nothing is silently dropped. */
    data class Unknown(
        val command: String,
        val data: JsonElement?,
    ) : GomuksEvent
}

/**
 * One line of the stream. [requestId] is 0 for per-connection messages, and a strictly
 * decreasing negative number for buffered events — the value to resume and acknowledge from.
 */
data class GomuksFrame(
    val command: String,
    val requestId: Long,
    val event: GomuksEvent,
)
