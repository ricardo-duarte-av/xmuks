package pt.aguiarvieira.xmuks.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert

/** Write side, used only by the sync ingestor inside its transactions. */
@Dao
interface SyncDao {
    @Upsert suspend fun upsertRooms(rooms: List<RoomEntity>)

    @Upsert suspend fun upsertEvents(events: List<EventEntity>)

    @Upsert suspend fun upsertState(state: List<RoomStateEntity>)

    @Upsert suspend fun upsertTimeline(rows: List<TimelineEntity>)

    @Upsert suspend fun upsertReceipts(receipts: List<ReceiptEntity>)

    @Upsert suspend fun upsertAccountData(data: List<AccountDataEntity>)

    @Upsert suspend fun upsertInvites(invites: List<InvitedRoomEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpaceEdges(edges: List<SpaceEdgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopLevelSpaces(spaces: List<TopLevelSpaceEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM rooms WHERE roomId = :roomId)")
    suspend fun roomExists(roomId: String): Boolean

    @Query("DELETE FROM timeline WHERE roomId = :roomId")
    suspend fun clearTimeline(roomId: String)

    @Query("DELETE FROM space_edges WHERE spaceId = :spaceId")
    suspend fun clearSpaceEdges(spaceId: String)

    @Query("DELETE FROM top_level_spaces")
    suspend fun clearTopLevelSpaces()

    @Query("DELETE FROM invited_rooms WHERE roomId = :roomId")
    suspend fun deleteInvite(roomId: String)

    // --- Removing a room and everything that hangs off it (left, or swept after a full sync) ---

    @Query("DELETE FROM rooms WHERE roomId IN (:roomIds)")
    suspend fun deleteRooms(roomIds: List<String>)

    @Query("DELETE FROM events WHERE roomId IN (:roomIds)")
    suspend fun deleteEventsOf(roomIds: List<String>)

    @Query("DELETE FROM room_state WHERE roomId IN (:roomIds)")
    suspend fun deleteStateOf(roomIds: List<String>)

    @Query("DELETE FROM timeline WHERE roomId IN (:roomIds)")
    suspend fun deleteTimelineOf(roomIds: List<String>)

    @Query("DELETE FROM receipts WHERE roomId IN (:roomIds)")
    suspend fun deleteReceiptsOf(roomIds: List<String>)

    @Query("DELETE FROM account_data WHERE roomId IN (:roomIds)")
    suspend fun deleteAccountDataOf(roomIds: List<String>)

    @Query("DELETE FROM invited_rooms WHERE roomId IN (:roomIds)")
    suspend fun deleteInvites(roomIds: List<String>)

    /** A space that is gone takes its child list with it; edges *to* it from other spaces stay. */
    @Query("DELETE FROM space_edges WHERE spaceId IN (:roomIds)")
    suspend fun deleteEdgesFrom(roomIds: List<String>)

    @Query("DELETE FROM top_level_spaces WHERE roomId IN (:roomIds)")
    suspend fun deleteTopLevel(roomIds: List<String>)

    // --- Full-sync sweep ---

    @Query("SELECT roomId FROM rooms WHERE generation < :generation")
    suspend fun roomsOlderThan(generation: Long): List<String>

    @Query("DELETE FROM account_data WHERE roomId = '' AND generation < :generation")
    suspend fun sweepGlobalAccountData(generation: Long)

    @Query("DELETE FROM invited_rooms WHERE generation < :generation")
    suspend fun sweepInvites(generation: Long)

    // --- Stream bookkeeping ---

    @Query("SELECT * FROM sync_meta WHERE id = 0")
    suspend fun meta(): SyncMetaEntity?

    @Upsert suspend fun saveMeta(meta: SyncMetaEntity)

    @Query("UPDATE sync_meta SET displayName = :displayName, avatar = :avatar WHERE id = 0 AND userId = :userId")
    suspend fun updateOwnProfile(
        userId: String,
        displayName: String?,
        avatar: String?,
    )

    /** Every avatar the room list can show, for background prefetching. */
    @Query(
        """
        SELECT DISTINCT avatar FROM rooms WHERE avatar IS NOT NULL
        UNION SELECT avatar FROM sync_meta WHERE avatar IS NOT NULL
        """,
    )
    suspend fun allAvatars(): List<String>

    /** Makes the next connection ask for a full snapshot (pull-to-refresh, or a suspected gap). */
    @Query("UPDATE sync_meta SET lastFullSyncAt = 0 WHERE id = 0")
    suspend fun markFullSyncDue()

    /** Stream position only: never touches the catch-up timestamp, which only the ingestor advances. */
    @Query("UPDATE sync_meta SET runId = :runId, listenerId = :listenerId, lastRequestId = :lastRequestId WHERE id = 0")
    suspend fun updateStreamPosition(
        runId: String?,
        listenerId: Long,
        lastRequestId: Long,
    )

    // --- Wiping (account change, identity change), inside the caller's transaction ---

    @Query("DELETE FROM rooms")
    suspend fun wipeRooms()

    @Query("DELETE FROM space_edges")
    suspend fun wipeSpaceEdges()

    @Query("DELETE FROM top_level_spaces")
    suspend fun wipeTopLevelSpaces()

    @Query("DELETE FROM events")
    suspend fun wipeEvents()

    @Query("DELETE FROM room_state")
    suspend fun wipeRoomState()

    @Query("DELETE FROM timeline")
    suspend fun wipeTimeline()

    @Query("DELETE FROM receipts")
    suspend fun wipeReceipts()

    @Query("DELETE FROM account_data")
    suspend fun wipeAccountData()

    @Query("DELETE FROM invited_rooms")
    suspend fun wipeInvites()

    @Query("DELETE FROM sync_meta")
    suspend fun wipeMeta()
}
