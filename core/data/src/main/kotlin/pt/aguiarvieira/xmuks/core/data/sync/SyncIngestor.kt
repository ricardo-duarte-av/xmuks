package pt.aguiarvieira.xmuks.core.data.sync

import androidx.room3.withWriteTransaction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.database.AccountDataEntity
import pt.aguiarvieira.xmuks.core.database.EventEntity
import pt.aguiarvieira.xmuks.core.database.InvitedRoomEntity
import pt.aguiarvieira.xmuks.core.database.ReceiptEntity
import pt.aguiarvieira.xmuks.core.database.RoomEntity
import pt.aguiarvieira.xmuks.core.database.RoomStateEntity
import pt.aguiarvieira.xmuks.core.database.SpaceEdgeEntity
import pt.aguiarvieira.xmuks.core.database.SyncDao
import pt.aguiarvieira.xmuks.core.database.SyncMetaEntity
import pt.aguiarvieira.xmuks.core.database.TimelineEntity
import pt.aguiarvieira.xmuks.core.database.TopLevelSpaceEntity
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.network.ResumeStore
import pt.aguiarvieira.xmuks.core.network.ResyncRequired
import pt.aguiarvieira.xmuks.core.protocol.AccountData
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.InvitedRoom
import pt.aguiarvieira.xmuks.core.protocol.Room
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom

/**
 * Applies the stream to the database — one write transaction per frame — and is the stream's
 * [ResumeStore], so where-we-are and what-we-have can never disagree.
 *
 * Freshness rules, because the cache must never show what no longer exists:
 * - Room metadata, account data content and space child lists are replaced whole, never merged.
 * - Left rooms are deleted with everything that references them.
 * - A full sync (`clear_state`) is mark-and-sweep: every row it delivers is stamped with a new
 *   generation, and at `init_complete` all older rows are deleted. Until then the catch-up timestamp
 *   stays 0, so an interrupted full sync is redone in full rather than "caught up" with holes.
 * - The data belongs to one Matrix user on one gomuks device; if the backend reports a different
 *   one, everything is wiped and a full sync forced.
 * - A full sync older than [fullResyncAfterMs] is redone anyway, as a backstop for anything a
 *   catch-up can miss.
 */
class SyncIngestor(
    private val db: XmuksDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fullResyncAfterMs: Long = DAY_MS,
) : ResumeStore,
    AccountScoped {
    private val dao: SyncDao = db.syncDao()

    suspend fun apply(frame: GomuksFrame) {
        when (val event = frame.event) {
            is GomuksEvent.ClientState -> checkIdentity(event)
            is GomuksEvent.Sync -> db.withWriteTransaction { applySync(event.sync) }
            GomuksEvent.InitComplete -> db.withWriteTransaction { finishFullSync() }
            is GomuksEvent.EventsDecrypted -> db.withWriteTransaction { applyDecrypted(event) }
            else -> Unit
        }
    }

    override suspend fun load(): ResumePoint {
        val meta = dao.meta() ?: return ResumePoint()
        val fullSyncDue =
            meta.fullSyncInProgress || meta.lastServerTs == 0L || clock() - meta.lastFullSyncAt > fullResyncAfterMs
        // Asking for a full sync means asking for nothing to be resumed either: with a valid run ID
        // and request ID gomuks would replay its buffer instead of sending a snapshot.
        if (fullSyncDue) return ResumePoint()
        return ResumePoint(meta.runId, meta.lastRequestId, meta.listenerId, meta.lastServerTs)
    }

    override suspend fun save(point: ResumePoint) {
        db.withWriteTransaction {
            if (dao.meta() == null) dao.saveMeta(SyncMetaEntity())
            dao.updateStreamPosition(point.runId, point.listenerId, point.lastRequestId)
        }
    }

    override suspend fun clearAccountData() {
        db.withWriteTransaction { wipe() }
    }

    private suspend fun wipe() {
        dao.wipeRooms()
        dao.wipeSpaceEdges()
        dao.wipeTopLevelSpaces()
        dao.wipeEvents()
        dao.wipeRoomState()
        dao.wipeTimeline()
        dao.wipeReceipts()
        dao.wipeAccountData()
        dao.wipeInvites()
        dao.wipeMeta()
    }

    // --- Identity -------------------------------------------------------------------------------

    private suspend fun checkIdentity(state: GomuksEvent.ClientState) {
        val userId = state.userId ?: return
        val mismatch =
            db.withWriteTransaction {
                val meta = dao.meta() ?: SyncMetaEntity()
                val known = meta.userId != null
                val same = meta.userId == userId && meta.deviceId == state.deviceId
                if (known && !same) {
                    wipe()
                    dao.saveMeta(SyncMetaEntity(userId = userId, deviceId = state.deviceId))
                } else if (!known) {
                    dao.saveMeta(meta.copy(userId = userId, deviceId = state.deviceId))
                }
                known && !same
            }
        if (mismatch) throw ResyncRequired("gomuks now serves $userId/${state.deviceId}; cached data discarded")
    }

    // --- Sync -----------------------------------------------------------------------------------

    private suspend fun applySync(sync: SyncComplete) {
        var meta = dao.meta() ?: SyncMetaEntity()
        if (sync.clearState) {
            meta =
                meta.copy(
                    generation = meta.generation + 1,
                    fullSyncInProgress = true,
                    lastServerTs = 0,
                    pendingServerTs = 0
                )
        }
        val generation = meta.generation

        sync.topLevelSpaces?.let { ids ->
            dao.clearTopLevelSpaces()
            dao.insertTopLevelSpaces(ids.mapIndexed { i, id -> TopLevelSpaceEntity(id, i) })
        }
        dao.upsertAccountData(sync.accountData.values.map { it.toEntity(roomId = "", generation) })
        dao.upsertInvites(sync.invitedRooms.map { it.toEntity(generation) })
        sync.rooms.forEach { (roomId, room) -> applyRoom(roomId, room, generation) }
        sync.spaceEdges.forEach { (spaceId, edges) ->
            dao.clearSpaceEdges(spaceId)
            dao.insertSpaceEdges(
                edges.map { SpaceEdgeEntity(spaceId, it.childId, it.order, it.suggested, it.canonical) },
            )
        }
        if (sync.leftRooms.isNotEmpty()) deleteRooms(sync.leftRooms)

        meta =
            if (meta.fullSyncInProgress) {
                meta.copy(pendingServerTs = maxOf(meta.pendingServerTs, sync.serverTimestamp))
            } else {
                meta.copy(lastServerTs = maxOf(meta.lastServerTs, sync.serverTimestamp))
            }
        dao.saveMeta(meta)
    }

    private suspend fun applyRoom(
        roomId: String,
        room: SyncRoom,
        generation: Long,
    ) {
        val meta = room.meta
        if (meta != null) {
            dao.upsertRooms(listOf(meta.toEntity(generation)))
            dao.deleteInvite(roomId) // joined (here or elsewhere): no longer an invite
        } else if (!dao.roomExists(roomId)) {
            return // e.g. catch-up account data for a room we don't have; nothing to hang it on
        }
        dao.upsertEvents(room.events.map { it.toEntity() })
        dao.upsertState(
            room.state.flatMap { (type, byKey) ->
                byKey.map { (key, rowId) -> RoomStateEntity(roomId, type, key, rowId) }
            },
        )
        if (room.reset) dao.clearTimeline(roomId)
        dao.upsertTimeline(room.timeline.map { TimelineEntity(roomId, it.timelineRowId, it.eventRowId) })
        dao.upsertAccountData(room.accountData.values.map { it.toEntity(roomId, generation) })
        dao.upsertReceipts(
            room.receipts.values.flatten().map {
                ReceiptEntity(roomId, it.userId, it.receiptType, it.threadId.orEmpty(), it.eventId, it.timestamp)
            },
        )
    }

    private suspend fun applyDecrypted(event: GomuksEvent.EventsDecrypted) {
        if (!dao.roomExists(event.roomId)) return
        dao.upsertEvents(event.events.map { it.toEntity() })
    }

    /** At init_complete of a full sync: delete everything the snapshot didn't contain. */
    private suspend fun finishFullSync() {
        val meta = dao.meta() ?: return
        if (!meta.fullSyncInProgress) return
        val stale = dao.roomsOlderThan(meta.generation)
        if (stale.isNotEmpty()) deleteRooms(stale)
        dao.sweepGlobalAccountData(meta.generation)
        dao.sweepInvites(meta.generation)
        dao.saveMeta(
            meta.copy(
                fullSyncInProgress = false,
                lastServerTs = meta.pendingServerTs,
                pendingServerTs = 0,
                lastFullSyncAt = clock(),
            ),
        )
    }

    private suspend fun deleteRooms(roomIds: List<String>) {
        roomIds.chunked(SQL_VARIABLE_CHUNK).forEach { ids ->
            dao.deleteEventsOf(ids)
            dao.deleteStateOf(ids)
            dao.deleteTimelineOf(ids)
            dao.deleteReceiptsOf(ids)
            dao.deleteAccountDataOf(ids)
            dao.deleteInvites(ids)
            dao.deleteEdgesFrom(ids)
            dao.deleteTopLevel(ids)
            dao.deleteRooms(ids)
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Stay well under SQLite's bound-parameter limit. */
        const val SQL_VARIABLE_CHUNK = 500
    }
}

private fun Room.toEntity(generation: Long) =
    RoomEntity(
        roomId = roomId,
        name = name?.takeIf { it.isNotEmpty() },
        nameQuality = nameQuality,
        avatar = avatar?.takeIf { it.isNotEmpty() },
        topic = topic?.takeIf { it.isNotEmpty() },
        canonicalAlias = canonicalAlias?.takeIf { it.isNotEmpty() },
        dmUserId = dmUserId,
        roomType = roomType,
        isSpace = isSpace,
        encrypted = encryptionEvent != null,
        replacementRoom = tombstone?.string("replacement_room"),
        heroes = lazyLoadSummary?.heroes.orEmpty().joinToString("\n"),
        joinedMembers = lazyLoadSummary?.joinedMemberCount,
        invitedMembers = lazyLoadSummary?.invitedMemberCount,
        previewEventRowId = previewEventRowId,
        sortingTs = sortingTimestamp,
        unreadHighlights = unreadHighlights,
        unreadNotifications = unreadNotifications,
        unreadMessages = unreadMessages,
        markedUnread = markedUnread == true,
        generation = generation,
    )

private fun Event.toEntity() =
    EventEntity(
        rowId = rowId,
        roomId = roomId,
        eventId = eventId,
        sender = sender,
        type = effectiveType,
        stateKey = stateKey,
        timestamp = timestamp,
        content = effectiveContent.toString(),
        unsigned = unsigned?.toString(),
        previewText = localContent?.previewText,
        sanitizedHtml = localContent?.sanitizedHtml,
        relatesTo = relatesTo,
        relationType = relationType,
        redactedBy = redactedBy,
        reactions = reactions?.let { r -> JsonObject(r.mapValues { JsonPrimitive(it.value) }).toString() },
        lastEditRowId = lastEditRowId?.takeIf { it != 0L },
        transactionId = transactionId,
        sendError = sendError,
        decryptionError = decryptionError,
    )

private fun AccountData.toEntity(
    roomId: String,
    generation: Long,
) = AccountDataEntity(roomId, type, content.toString(), generation)

/** Pulls display bits out of the stripped invite state. */
private fun InvitedRoom.toEntity(generation: Long): InvitedRoomEntity {
    fun stateOf(type: String) = inviteState.firstOrNull { it.string("type") == type }?.get("content")?.jsonObject
    val member =
        inviteState.lastOrNull {
            it.string("type") == "m.room.member" &&
                it.content()?.string("membership") == "invite"
        }
    return InvitedRoomEntity(
        roomId = roomId,
        createdAt = createdAt,
        name = stateOf("m.room.name")?.string("name"),
        avatar = stateOf("m.room.avatar")?.string("url"),
        inviter = member?.string("sender"),
        isDirect = (member?.content()?.get("is_direct") as? JsonPrimitive)?.booleanOrNull == true,
        inviteState = inviteState.toString(),
        generation = generation,
    )
}

private fun JsonObject.content() = get("content") as? JsonObject

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
