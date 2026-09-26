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
}
