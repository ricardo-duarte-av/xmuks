package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.database.RoomEntity
import pt.aguiarvieira.xmuks.core.database.RoomSummaryRow
import pt.aguiarvieira.xmuks.core.database.SpaceSummaryRow
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

    fun directMessages(): Flow<List<RoomSummary>> = rooms(dao.directMessages())

    fun roomsInSpace(spaceId: String): Flow<List<RoomSummary>> = rooms(dao.roomsInSpace(spaceId))

    fun room(roomId: String): Flow<RoomSummary?> =
        combine(dao.roomSummary(roomId), me) { row, me -> row?.toSummary(me) }

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
        combine(source, me) { rows, me -> rows.map { it.toSummary(me) } }
            .distinctUntilChanged()
            .throttleLatest(UI_THROTTLE_MS)
            .flowOn(Dispatchers.Default)

    private fun RoomSummaryRow.toSummary(me: String?) =
        RoomSummary(
            roomId = roomId,
            name = name ?: roomId,
            avatarUrl = media.avatar(avatar),
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

/** Emits the first value at once, then at most one (the latest) per [periodMs]. */
internal fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform {
        emit(it)
        delay(periodMs)
    }

/** `@alice:example.org` → `alice`. */
internal fun localpart(userId: String): String = userId.removePrefix("@").substringBefore(':')
