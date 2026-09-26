package pt.aguiarvieira.xmuks.core.database

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

data class CacheCounts(
    val rooms: Int,
    val spaces: Int,
    val dms: Int,
    val topLevelSpaces: Int,
    val invites: Int,
)

/** A room as the room list shows it: metadata plus its preview event and the preview sender's name. */
data class RoomSummaryRow(
    val roomId: String,
    val name: String?,
    val avatar: String?,
    val dmUserId: String?,
    val isSpace: Boolean,
    val encrypted: Boolean,
    val sortingTs: Long,
    val unreadMessages: Int,
    val unreadNotifications: Int,
    val unreadHighlights: Int,
    val markedUnread: Boolean,
    val previewText: String?,
    val previewType: String?,
    val previewSender: String?,
    /** The sender's current display name in this room, from member state (so renames apply at once). */
    val previewSenderName: String?,
    val previewTs: Long?,
)

/** A space with unread totals over every room below it (subspaces included, each room counted once). */
data class SpaceSummaryRow(
    val roomId: String,
    val name: String?,
    val avatar: String?,
    val rooms: Int,
    val unreadRooms: Int,
    val unreadNotifications: Int,
    val unreadHighlights: Int,
)

data class OwnProfileRow(
    val userId: String?,
    val displayName: String?,
    val avatar: String?,
)

/** Read side. Everything is a Flow: screens re-render whenever a sync transaction commits. */
@Dao
interface RoomListDao {
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM rooms WHERE isSpace = 0) AS rooms,
            (SELECT COUNT(*) FROM rooms WHERE isSpace = 1) AS spaces,
            (SELECT COUNT(*) FROM rooms WHERE dmUserId IS NOT NULL) AS dms,
            (SELECT COUNT(*) FROM top_level_spaces) AS topLevelSpaces,
            (SELECT COUNT(*) FROM invited_rooms) AS invites
        """,
    )
    fun counts(): Flow<CacheCounts>

    @Query("SELECT * FROM sync_meta WHERE id = 0")
    fun meta(): Flow<SyncMetaEntity?>

    @Query("SELECT userId, displayName, avatar FROM sync_meta WHERE id = 0")
    fun ownProfile(): Flow<OwnProfileRow?>

    @Query("SELECT * FROM rooms WHERE roomId = :roomId")
    fun room(roomId: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms ORDER BY sortingTs DESC")
    fun roomsByRecency(): Flow<List<RoomEntity>>

    @Query("SELECT r.* FROM top_level_spaces t JOIN rooms r ON r.roomId = t.roomId ORDER BY t.position")
    fun topLevelSpaces(): Flow<List<RoomEntity>>

    /** A space's joined children, in the space's own order (`order`, then room ID, as per the spec). */
    @Query(
        """
        SELECT r.* FROM space_edges e JOIN rooms r ON r.roomId = e.childId
        WHERE e.spaceId = :spaceId
        ORDER BY CASE WHEN e."order" = '' THEN 1 ELSE 0 END, e."order", r.roomId
        """,
    )
    fun spaceChildren(spaceId: String): Flow<List<RoomEntity>>

    /** Every joined non-space room that isn't a DM, most recent first. */
    @Query(
        """
        SELECT r.roomId, r.name, r.avatar, r.dmUserId, r.isSpace, r.encrypted, r.sortingTs,
            r.unreadMessages, r.unreadNotifications, r.unreadHighlights, r.markedUnread,
            e.previewText AS previewText, e.type AS previewType, e.sender AS previewSender, e.timestamp AS previewTs,
            (SELECT json_extract(m.content, '$.displayname') FROM room_state s JOIN events m ON m.rowId = s.eventRowId
             WHERE s.roomId = r.roomId AND s.type = 'm.room.member' AND s.stateKey = e.sender) AS previewSenderName
        FROM rooms r LEFT JOIN events e ON e.rowId = r.previewEventRowId
        WHERE r.isSpace = 0 AND r.dmUserId IS NULL
        ORDER BY r.sortingTs DESC
        """
    )
    fun chats(): Flow<List<RoomSummaryRow>>

    @Query(
        """
        SELECT r.roomId, r.name, r.avatar, r.dmUserId, r.isSpace, r.encrypted, r.sortingTs,
            r.unreadMessages, r.unreadNotifications, r.unreadHighlights, r.markedUnread,
            e.previewText AS previewText, e.type AS previewType, e.sender AS previewSender, e.timestamp AS previewTs,
            (SELECT json_extract(m.content, '$.displayname') FROM room_state s JOIN events m ON m.rowId = s.eventRowId
             WHERE s.roomId = r.roomId AND s.type = 'm.room.member' AND s.stateKey = e.sender) AS previewSenderName
        FROM rooms r LEFT JOIN events e ON e.rowId = r.previewEventRowId
        WHERE r.dmUserId IS NOT NULL AND r.isSpace = 0
        ORDER BY r.sortingTs DESC
        """
    )
    fun directMessages(): Flow<List<RoomSummaryRow>>

    /**
     * Rooms anywhere below [spaceId] (through subspaces, cycle-safe via UNION), most recent first.
     * Only joined rooms appear: edges to rooms we're not in join to nothing.
     */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
            SELECT childId FROM space_edges WHERE spaceId = :spaceId
            UNION
            SELECT e.childId FROM space_edges e JOIN tree t ON e.spaceId = t.id
        )
        SELECT r.roomId, r.name, r.avatar, r.dmUserId, r.isSpace, r.encrypted, r.sortingTs,
            r.unreadMessages, r.unreadNotifications, r.unreadHighlights, r.markedUnread,
            e.previewText AS previewText, e.type AS previewType, e.sender AS previewSender, e.timestamp AS previewTs,
            (SELECT json_extract(m.content, '$.displayname') FROM room_state s JOIN events m ON m.rowId = s.eventRowId
             WHERE s.roomId = r.roomId AND s.type = 'm.room.member' AND s.stateKey = e.sender) AS previewSenderName
        FROM rooms r LEFT JOIN events e ON e.rowId = r.previewEventRowId
        WHERE r.isSpace = 0 AND r.roomId IN (SELECT id FROM tree)
        ORDER BY r.sortingTs DESC
        """
    )
    fun roomsInSpace(spaceId: String): Flow<List<RoomSummaryRow>>

    /** Joined subspaces directly under [spaceId], in the space's order. */
    @Query(
        """
        SELECT r.* FROM space_edges e JOIN rooms r ON r.roomId = e.childId
        WHERE e.spaceId = :spaceId AND r.isSpace = 1
        ORDER BY CASE WHEN e."order" = '' THEN 1 ELSE 0 END, e."order", r.roomId
        """
    )
    fun subspaces(spaceId: String): Flow<List<RoomEntity>>

    /** Top-level spaces in gomuks' order, each with unread totals over all rooms below it. */
    @Query(
        """
        WITH RECURSIVE tree(root, id) AS (
            SELECT roomId, roomId FROM top_level_spaces
            UNION
            SELECT t.root, e.childId FROM tree t JOIN space_edges e ON e.spaceId = t.id
        ),
        totals AS (
            SELECT t.root AS root,
                COUNT(r.roomId) AS rooms,
                SUM(CASE WHEN r.unreadMessages > 0 OR r.markedUnread THEN 1 ELSE 0 END) AS unreadRooms,
                SUM(r.unreadNotifications) AS unreadNotifications,
                SUM(r.unreadHighlights) AS unreadHighlights
            FROM tree t JOIN rooms r ON r.roomId = t.id AND r.isSpace = 0
            GROUP BY t.root
        )
        SELECT s.roomId, s.name, s.avatar,
            COALESCE(x.rooms, 0) AS rooms, COALESCE(x.unreadRooms, 0) AS unreadRooms,
            COALESCE(x.unreadNotifications, 0) AS unreadNotifications,
            COALESCE(x.unreadHighlights, 0) AS unreadHighlights
        FROM top_level_spaces p JOIN rooms s ON s.roomId = p.roomId LEFT JOIN totals x ON x.root = p.roomId
        ORDER BY p.position
        """
    )
    fun topLevelSpaceSummaries(): Flow<List<SpaceSummaryRow>>

    @Query(
        """
        SELECT r.roomId, r.name, r.avatar, r.dmUserId, r.isSpace, r.encrypted, r.sortingTs,
            r.unreadMessages, r.unreadNotifications, r.unreadHighlights, r.markedUnread,
            e.previewText AS previewText, e.type AS previewType, e.sender AS previewSender, e.timestamp AS previewTs,
            (SELECT json_extract(m.content, '$.displayname') FROM room_state s JOIN events m ON m.rowId = s.eventRowId
             WHERE s.roomId = r.roomId AND s.type = 'm.room.member' AND s.stateKey = e.sender) AS previewSenderName
        FROM rooms r LEFT JOIN events e ON e.rowId = r.previewEventRowId
        WHERE r.roomId = :roomId
        """
    )
    fun roomSummary(roomId: String): Flow<RoomSummaryRow?>
}
