package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.sync.MemberStore
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** Someone's name and avatar in one room (their member event), and the room's name. */
data class RoomProfile(
    val roomName: String,
    val displayName: String?,
    val avatarMxc: String?,
)

/**
 * Per-room profiles: the member event as gomuks has it now, or the one we hold when gomuks can't
 * be asked. Ours can be stale (a catch-up sync only brings the preview sender's), so a fresh one
 * is written back.
 */
class RoomProfiles(
    database: XmuksDatabase,
    private val rooms: RoomListRepository,
    private val exec: ExecClient,
    private val members: MemberStore,
) {
    private val dao = database.roomListDao()

    /** [userId]'s profile in [roomId]; null when there's no member event for them there. */
    suspend fun of(
        roomId: String,
        userId: String,
    ): RoomProfile? {
        val content = fetch(roomId, userId) ?: cached(roomId, userId) ?: return null
        return RoomProfile(
            roomName = rooms.room(roomId).first()?.name ?: roomId,
            displayName = content.str("displayname")?.takeIf { it.isNotBlank() },
            avatarMxc = content.str("avatar_url")?.takeIf { it.startsWith("mxc://") },
        )
    }

    private suspend fun fetch(
        roomId: String,
        userId: String,
    ): JsonObject? {
        val key =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(MEMBER))
                put("state_key", JsonPrimitive(userId))
            }
        val result =
            exec.exec("get_specific_room_state", buildJsonObject { put("keys", JsonArray(listOf(key))) }, ExecMode.Read)
                as? ExecResult.Ok ?: return null
        val events =
            runCatching { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), result.data) }
                .getOrNull() ?: return null
        val member = events.firstOrNull { it.type == MEMBER && it.stateKey == userId } ?: return null
        members.store(roomId, listOf(member))
        return member.content
    }

    private suspend fun cached(
        roomId: String,
        userId: String,
    ): JsonObject? {
        val row = dao.memberEvents(roomId).firstOrNull { it.userId == userId } ?: return null
        return runCatching { GomuksJson.parseToJsonElement(row.content) as? JsonObject }.getOrNull()
    }

    private companion object {
        const val MEMBER = "m.room.member"
    }
}
