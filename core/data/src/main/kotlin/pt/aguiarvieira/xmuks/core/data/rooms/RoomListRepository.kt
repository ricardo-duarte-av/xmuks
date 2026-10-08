package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.database.RoomEntity
import pt.aguiarvieira.xmuks.core.database.RoomSummaryRow
import pt.aguiarvieira.xmuks.core.database.SpaceSummaryRow
import pt.aguiarvieira.xmuks.core.database.TabUnreadRow
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase

/** Room-list data straight from the database: every emission reflects the last committed sync. */
class RoomListRepository(
    database: XmuksDatabase,
    private val media: MediaUrls,
) {
    private val dao = database.roomListDao()
    private val me: Flow<String?> = dao.meta().map { it?.userId }.distinctUntilChanged()

    fun ownProfile(): Flow<OwnProfile?> =
        dao
            .ownProfile()
            .map { row ->
                val userId = row?.userId ?: return@map null
                OwnProfile(userId, row.displayName ?: localpart(userId), media.avatar(row.avatar))
            }.distinctUntilChanged()

    fun chats(): Flow<List<RoomSummary>> = rooms(dao.chats())

    fun tabBadges(): Flow<TabBadges> =
        combine(
            dao.tabUnread(dmsOnly = false),
            dao.tabUnread(dmsOnly = true),
            dao.spacesTabUnread()
        ) { chats, dms, spaces ->
            TabBadges(chats.toUnread(), dms.toUnread(), spaces.toUnread())
        }.distinctUntilChanged()
            .throttleLatest(UI_THROTTLE_MS)
            .flowOn(Dispatchers.Default)

    private fun TabUnreadRow.toUnread() = Unread(unreadRooms, notifyingRooms, mentionRooms)

    fun directMessages(): Flow<List<RoomSummary>> = rooms(dao.directMessages())

    fun roomsInSpace(spaceId: String): Flow<List<RoomSummary>> = rooms(dao.roomsInSpace(spaceId))

    fun room(roomId: String): Flow<RoomSummary?> =
        combine(
            dao.roomSummary(roomId),
            me,
            callRooms
        ) { row, me, calls -> row?.toSummary(me)?.copy(call = calls[roomId]) }

    /** Rooms with a call going on (expiry checked whenever the list changes anyway). */
    private val callRooms: Flow<Map<String, CallBadge>> =
        dao
            .callMembers()
            .map { rows ->
                val now = System.currentTimeMillis()
                rows
                    .filter { it.expiresAt > now }
                    .groupBy { it.roomId }
                    .mapValues { (_, members) ->
                        if (members.any { it.intent == "video" }) CallBadge.Video else CallBadge.Voice
                    }
            }.distinctUntilChanged()

    /** Those of [roomIds] we're in (once, not live), most recently active first. */
    suspend fun rooms(roomIds: List<String>): List<RoomSummary> {
        val ownId = me.first()
        // SQLite caps a query's parameters: a long list goes in slices.
        return roomIds
            .chunked(MAX_IDS_PER_QUERY)
            .flatMap { dao.roomSummaries(it) }
            .sortedByDescending { it.sortingTs }
            .map { it.toSummary(ownId) }
    }

    fun topLevelSpaces(): Flow<List<SpaceSummary>> =
        dao.topLevelSpaceSummaries().map { rows ->
            rows.map {
                it.toSummary()
            }
        }

    fun subspaces(spaceId: String): Flow<List<SpaceSummary>> =
        dao.subspaces(spaceId).map { rows -> rows.map { it.toSpaceSummary() } }

    fun space(spaceId: String): Flow<SpaceSummary?> = dao.room(spaceId).map { it?.toSpaceSummary() }

    /**
     * Busy accounts commit several syncs a second, each invalidating these queries. Map off the main
     * thread, drop identical results, and emit at most every [UI_THROTTLE_MS] (always the latest).
     */
    private fun rooms(source: Flow<List<RoomSummaryRow>>) =
        combine(source, me, callRooms) { rows, me, calls ->
            rows.map { it.toSummary(me).copy(call = calls[it.roomId]) }
        }.distinctUntilChanged()
            .throttleLatest(UI_THROTTLE_MS)
            .flowOn(Dispatchers.Default)

    private fun RoomSummaryRow.toSummary(me: String?) =
        RoomSummary(
            roomId = roomId,
            name = name ?: roomId,
            avatarUrl = media.avatar(avatar),
            avatarMxc = avatar,
            isDirect = dmUserId != null,
            encrypted = encrypted,
            // Without the sender's member state, `@alice:example.org` reads better as `alice`.
            previewSender = previewSenderName?.takeIf { it.isNotBlank() } ?: previewSender?.let(::localpart),
            previewFromMe = previewSender != null && previewSender == me,
            preview =
                when {
                    previewType == null -> Preview.None
                    previewType == "m.sticker" -> Preview.Sticker
                    previewType == "m.room.encrypted" -> Preview.Encrypted
                    !previewText.isNullOrBlank() -> Preview.Text(previewText!!)
                    else -> Preview.None
                },
            timestamp = maxOf(sortingTs, previewTs ?: 0),
            unread = Unread(unreadMessages, unreadNotifications, unreadHighlights, markedUnread),
            bridgeProtocol = bridgeProtocol,
            bridgeAvatarUrl = media.avatar(bridgeAvatar),
        )

    private fun SpaceSummaryRow.toSummary() =
        SpaceSummary(
            roomId = roomId,
            name = name ?: roomId,
            avatarUrl = media.avatar(avatar),
            rooms = rooms,
            unread = Unread(unreadRooms, unreadNotifications, unreadHighlights),
        )

    private fun RoomEntity.toSpaceSummary() =
        SpaceSummary(roomId, name ?: roomId, media.avatar(avatar), rooms = 0, unread = Unread())
}

private const val UI_THROTTLE_MS = 250L
private const val MAX_IDS_PER_QUERY = 500

/** Emits the first value at once, then at most one (the latest) per [periodMs]. */
internal fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform {
        emit(it)
        delay(periodMs)
    }

/** `@alice:example.org` → `alice`. */
internal fun localpart(userId: String): String = userId.removePrefix("@").substringBefore(':')
