package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** Someone's name and avatar in one room (their member event), and the room's name. */
data class RoomProfile(
    val roomName: String,
    val displayName: String?,
    val avatarMxc: String?,
)

/** Per-room profiles, from the member events we hold. */
class RoomProfiles(
    database: XmuksDatabase,
    private val rooms: RoomListRepository,
) {
    private val dao = database.roomListDao()

    /** [userId]'s profile in [roomId]; null when we hold no member event for them there. */
    suspend fun of(
        roomId: String,
        userId: String,
    ): RoomProfile? {
        val row = dao.memberEvents(roomId).firstOrNull { it.userId == userId } ?: return null
        val content =
            runCatching { GomuksJson.parseToJsonElement(row.content) as? JsonObject }.getOrNull() ?: return null
        return RoomProfile(
            roomName = rooms.room(roomId).first()?.name ?: roomId,
            displayName = content.str("displayname")?.takeIf { it.isNotBlank() },
            avatarMxc = content.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
        )
    }
}
