package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Wire types for gomuks' `sync_complete` (pkg/hicli/jsoncmd/events.go, pkg/hicli/database). Field docs
// paraphrase upstream's, including how each field must be applied — replace vs. merge matters.

/** One `sync_complete` payload. The initial sync arrives as several of these (≤100 rooms each). */
@Serializable
data class SyncComplete(
    /** Timestamp to pass back as `last_server_ts` for a catch-up sync after a long disconnect. */
    @SerialName("server_timestamp") val serverTimestamp: Long = 0,
    /** True when this payload is a catch-up (delta since `last_server_ts`), not a full snapshot. */
    val catchup: Boolean = false,
    /** Throw away everything cached before applying this payload. */
    @SerialName("clear_state") val clearState: Boolean = false,
    /** Changed global account data; each entry replaces the old content entirely. */
    @SerialName("account_data") val accountData: Map<String, AccountData> = emptyMap(),
    val rooms: Map<String, SyncRoom> = emptyMap(),
    /** Rooms the user left; delete everything about them. */
    @SerialName("left_rooms") val leftRooms: List<String> = emptyList(),
    @SerialName("invited_rooms") val invitedRooms: List<InvitedRoom> = emptyList(),
    /** Per space, the complete list of child edges: replaces the previous list for that space. */
    @SerialName("space_edges") val spaceEdges: Map<String, List<SpaceEdge>> = emptyMap(),
    /** When non-null, the complete list of top-level spaces (replace, don't merge). */
    @SerialName("top_level_spaces") val topLevelSpaces: List<String>? = null,
)

@Serializable
data class SyncRoom(
    /** Replaces the cached room metadata entirely. */
    val meta: Room? = null,
    /** Timeline additions, appended — unless [reset], in which case they replace the timeline. */
    val timeline: List<TimelineRowTuple> = emptyList(),
    val reset: Boolean = false,
    /** state type → state key → event rowid; deep-merged into the existing state map. */
    val state: Map<String, Map<String, Long>> = emptyMap(),
    @SerialName("account_data") val accountData: Map<String, AccountData> = emptyMap(),
    /** Events referenced by [timeline], [state], previews etc. May repeat events already known. */
    val events: List<Event> = emptyList(),
    /** event ID → receipts; keep only the newest receipt per user. */
    val receipts: Map<String, List<Receipt>> = emptyMap(),
    @SerialName("dismiss_notifications") val dismissNotifications: Boolean = false,
    @SerialName("dismiss_up_to") val dismissUpTo: String? = null,
    @SerialName("dismiss_up_to_ts") val dismissUpToTs: Long = 0,
    val notifications: List<SyncNotification> = emptyList(),
)

/** Room metadata (`database.Room`). */
@Serializable
data class Room(
    @SerialName("room_id") val roomId: String,
    @SerialName("creation_content") val creationContent: JsonObject? = null,
    val tombstone: JsonObject? = null,
    val name: String? = null,
    @SerialName("name_quality") val nameQuality: Int = 0,
    val avatar: String? = null,
    @SerialName("explicit_avatar") val explicitAvatar: Boolean = false,
    @SerialName("dm_user_id") val dmUserId: String? = null,
    val topic: String? = null,
    @SerialName("canonical_alias") val canonicalAlias: String? = null,
    @SerialName("lazy_load_summary") val lazyLoadSummary: LazyLoadSummary? = null,
    @SerialName("encryption_event") val encryptionEvent: JsonObject? = null,
    @SerialName("has_member_list") val hasMemberList: Boolean = false,
    @SerialName("preview_event_rowid") val previewEventRowId: Long = 0,
    @SerialName("sorting_timestamp") val sortingTimestamp: Long = 0,
    @SerialName("unread_highlights") val unreadHighlights: Int = 0,
    @SerialName("unread_notifications") val unreadNotifications: Int = 0,
    @SerialName("unread_messages") val unreadMessages: Int = 0,
    @SerialName("marked_unread") val markedUnread: Boolean? = null,
) {
    /** `m.room.create` → `type`; `"m.space"` for spaces. */
    val roomType: String?
        get() = (creationContent?.get("type") as? JsonPrimitive)?.contentOrNull

    val isSpace: Boolean get() = roomType == ROOM_TYPE_SPACE

    companion object {
        const val ROOM_TYPE_SPACE = "m.space"
    }
}

@Serializable
data class LazyLoadSummary(
    @SerialName("m.heroes") val heroes: List<String> = emptyList(),
    @SerialName("m.joined_member_count") val joinedMemberCount: Int? = null,
    @SerialName("m.invited_member_count") val invitedMemberCount: Int? = null,
)

@Serializable
data class TimelineRowTuple(
    @SerialName("timeline_rowid") val timelineRowId: Long,
    @SerialName("event_rowid") val eventRowId: Long,
)

/** A gomuks event row (`database.Event`). For encrypted events, [decrypted] holds the cleartext. */
@Serializable
data class Event(
    @SerialName("rowid") val rowId: Long,
    @SerialName("timeline_rowid") val timelineRowId: Long = 0,
    @SerialName("room_id") val roomId: String,
    @SerialName("event_id") val eventId: String,
    val sender: String,
    val type: String,
    @SerialName("state_key") val stateKey: String? = null,
    val timestamp: Long = 0,
    val content: JsonObject = JsonObject(emptyMap()),
    val decrypted: JsonObject? = null,
    @SerialName("decrypted_type") val decryptedType: String? = null,
    val unsigned: JsonObject? = null,
    @SerialName("local_content") val localContent: LocalContent? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("redacted_by") val redactedBy: String? = null,
    @SerialName("relates_to") val relatesTo: String? = null,
    @SerialName("relation_type") val relationType: String? = null,
    @SerialName("decryption_error") val decryptionError: String? = null,
    @SerialName("send_error") val sendError: String? = null,
    /** Reaction key → count, aggregated by gomuks. Keys may be `mxc://` URIs (custom emoji). */
    val reactions: Map<String, Int>? = null,
    @SerialName("last_edit_rowid") val lastEditRowId: Long? = null,
    @SerialName("unread_type") val unreadType: Int = 0,
) {
    /** The type to render: the decrypted type for encrypted events. */
    val effectiveType: String get() = decryptedType ?: type

    /** The content to render: the decrypted content for encrypted events. */
    val effectiveContent: JsonObject get() = decrypted ?: content
}

/** Client-side data gomuks derives for an event. */
@Serializable
data class LocalContent(
    @SerialName("sanitized_html") val sanitizedHtml: String? = null,
    /** Plain-text preview for the room list and notifications. */
    @SerialName("preview_text") val previewText: String? = null,
    @SerialName("was_plaintext") val wasPlaintext: Boolean = false,
    @SerialName("big_emoji") val bigEmoji: Boolean = false,
    @SerialName("has_math") val hasMath: Boolean = false,
    @SerialName("edit_source") val editSource: String? = null,
    @SerialName("push_rule_id") val pushRuleId: String? = null,
)

@Serializable
data class SpaceEdge(
    @SerialName("space_id") val spaceId: String? = null,
    @SerialName("child_id") val childId: String,
    @SerialName("child_event_rowid") val childEventRowId: Long = 0,
    val order: String = "",
    val suggested: Boolean = false,
    @SerialName("parent_event_rowid") val parentEventRowId: Long = 0,
    val canonical: Boolean = false,
)

@Serializable
data class Receipt(
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("receipt_type") val receiptType: String,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("event_id") val eventId: String,
    val timestamp: Long = 0,
)

@Serializable
data class AccountData(
    @SerialName("user_id") val userId: String = "",
    @SerialName("room_id") val roomId: String? = null,
    val type: String,
    val content: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class InvitedRoom(
    @SerialName("room_id") val roomId: String,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("invite_state") val inviteState: List<JsonObject> = emptyList(),
)

@Serializable
data class SyncNotification(
    @SerialName("event_rowid") val eventRowId: Long,
    val sound: Boolean = false,
    val highlight: Boolean = false,
)
