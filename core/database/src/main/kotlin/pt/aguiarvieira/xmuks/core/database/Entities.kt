package pt.aguiarvieira.xmuks.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// The local mirror of gomuks' state. Freshness rules (see SyncIngestor):
//  - rows that gomuks sends whole are *replaced*, never merged: an absent field means "removed";
//  - a full sync marks every row it delivers with the current [SyncMetaEntity.generation] and, at
//    init_complete, deletes whatever it didn't deliver (rooms left or removed while we were away);
//  - left rooms delete the room and everything hanging off it.

@Entity(tableName = "rooms", indices = [Index("sortingTs"), Index("dmUserId"), Index("generation")])
data class RoomEntity(
    @PrimaryKey val roomId: String,
    val name: String?,
    val nameQuality: Int,
    /** `mxc://` URI. */
    val avatar: String?,
    val topic: String?,
    val canonicalAlias: String?,
    val dmUserId: String?,
    /** `m.room.create` type, e.g. `m.space`. */
    val roomType: String?,
    val isSpace: Boolean,
    val encrypted: Boolean,
    /** Room ID this one was upgraded to, if tombstoned. */
    val replacementRoom: String?,
    /** Heroes as a newline-separated list of user IDs (display fallback for unnamed rooms). */
    val heroes: String,
    val joinedMembers: Int?,
    val invitedMembers: Int?,
    val previewEventRowId: Long,
    val sortingTs: Long,
    val unreadHighlights: Int,
    val unreadNotifications: Int,
    val unreadMessages: Int,
    val markedUnread: Boolean,
    val generation: Long,
)

/** Complete child list per space; replaced wholesale whenever gomuks sends a space's edges. */
@Entity(tableName = "space_edges", primaryKeys = ["spaceId", "childId"], indices = [Index("childId")])
data class SpaceEdgeEntity(
    val spaceId: String,
    val childId: String,
    val order: String,
    val suggested: Boolean,
    val canonical: Boolean,
)

/** Replaced wholesale whenever gomuks sends `top_level_spaces`. */
@Entity(tableName = "top_level_spaces")
data class TopLevelSpaceEntity(
    @PrimaryKey val roomId: String,
    val position: Int,
)

/** Keyed by gomuks' event rowid, which is stable for the lifetime of the backend database. */
@Entity(
    tableName = "events",
    indices = [Index("roomId"), Index("eventId"), Index("relatesTo")],
)
data class EventEntity(
    @PrimaryKey val rowId: Long,
    val roomId: String,
    val eventId: String,
    val sender: String,
    /** Effective type: the decrypted type for encrypted events. */
    val type: String,
    val stateKey: String?,
    val timestamp: Long,
    /** Effective (decrypted) content, as JSON. */
    val content: String,
    val unsigned: String?,
    val previewText: String?,
    val sanitizedHtml: String?,
    val relatesTo: String?,
    val relationType: String?,
    val redactedBy: String?,
    /** Reaction key → count as JSON; keys may be `mxc://` URIs. */
    val reactions: String?,
    val lastEditRowId: Long?,
    val transactionId: String?,
    val sendError: String?,
    val decryptionError: String?,
)

/** Current room state: (type, state key) → event rowid. Deep-merged from syncs. */
@Entity(tableName = "room_state", primaryKeys = ["roomId", "type", "stateKey"])
data class RoomStateEntity(
    val roomId: String,
    val type: String,
    val stateKey: String,
    val eventRowId: Long,
)

/** Timeline order. gomuks resets a room's rows (`reset: true`) when it reorders them. */
@Entity(tableName = "timeline", primaryKeys = ["roomId", "timelineRowId"], indices = [Index("eventRowId")])
data class TimelineEntity(
    val roomId: String,
    val timelineRowId: Long,
    val eventRowId: Long,
)

/** Latest receipt per (user, type, thread). */
@Entity(
    tableName = "receipts",
    primaryKeys = ["roomId", "userId", "receiptType", "threadId"],
    indices = [Index("eventId")]
)
data class ReceiptEntity(
    val roomId: String,
    val userId: String,
    val receiptType: String,
    /** Empty for unthreaded receipts. */
    val threadId: String,
    val eventId: String,
    val timestamp: Long,
)

/** Global (roomId = "") and per-room account data; content replaced wholesale. */
@Entity(tableName = "account_data", primaryKeys = ["roomId", "type"], indices = [Index("generation")])
data class AccountDataEntity(
    val roomId: String,
    val type: String,
    val content: String,
    val generation: Long,
)

@Entity(tableName = "invited_rooms", indices = [Index("generation")])
data class InvitedRoomEntity(
    @PrimaryKey val roomId: String,
    val createdAt: Long,
    val name: String?,
    val avatar: String?,
    val inviter: String?,
    val isDirect: Boolean,
    /** Stripped invite state, as a JSON array. */
    val inviteState: String,
    val generation: Long,
)

/**
 * Single row describing what the cache contains and where the stream is up to. Written in the same
 * transaction as the data, so it can never claim more than the tables hold.
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = 0,
    /** Identity of the data: a different Matrix user or gomuks device means none of it applies. */
    val userId: String? = null,
    val deviceId: String? = null,
    val runId: String? = null,
    val lastRequestId: Long = 0,
    val listenerId: Long = 0,
    /** Catch-up point; 0 means "no complete sync yet — ask for a full one". */
    val lastServerTs: Long = 0,
    /** Highest server timestamp seen during the full sync in progress; promoted at init_complete. */
    val pendingServerTs: Long = 0,
    @ColumnInfo(defaultValue = "0") val fullSyncInProgress: Boolean = false,
    val lastFullSyncAt: Long = 0,
    val generation: Long = 0,
)
