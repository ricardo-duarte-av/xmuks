package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** How a save went: the rooms whose change didn't go through, and rooms that keep pointing here. */
data class SpaceEditResult(
    val failed: Set<String> = emptySet(),
    /**
     * Taken out, but the room itself still names the space as its parent (`m.space.parent`) and we
     * may not change that: gomuks (and other clients) keep showing it in the space.
     */
    val parentKept: Set<String> = emptySet(),
)

/**
 * Adds rooms to a space and takes them out. A space's children are separate `m.space.child` state
 * events (state key: the child's room ID), so a save only sends what changed; children nobody
 * touched, and rooms we're not in, are left exactly as they are.
 */
class SpaceEditRepository(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val roomInfo: RoomInfoRepository,
    private val media: MediaUrls,
) {
    private val dao = database.roomListDao()

    fun data(spaceId: String): Flow<SpaceEditData> =
        combine(dao.roomsByRecency(), dao.spaceEdges()) { rooms, edges ->
            SpaceEdits.data(spaceId, rooms, edges, media::avatar)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Whether we may change [spaceId]'s children. */
    suspend fun canEdit(spaceId: String): Boolean {
        val me = roomInfo.me.first() ?: return false
        return roomInfo.powerLevels(spaceId).getOrNull()?.canSetState(me, CHILD) == true
    }

    suspend fun save(
        spaceId: String,
        add: Set<String>,
        remove: Set<String>,
    ): SpaceEditResult {
        val me = roomInfo.me.first() ?: return SpaceEditResult(failed = add + remove)
        val failed = mutableSetOf<String>()
        val parentKept = mutableSetOf<String>()
        remove.forEach { childId ->
            when (removeChild(me, spaceId, childId)) {
                Removal.Failed -> failed += childId
                Removal.ParentKept -> parentKept += childId
                Removal.Done -> Unit
            }
        }
        add.forEach { childId -> if (!addChild(me, spaceId, childId)) failed += childId }
        return SpaceEditResult(failed, parentKept)
    }

    private enum class Removal { Done, ParentKept, Failed }

    private suspend fun removeChild(
        me: String,
        spaceId: String,
        childId: String,
    ): Removal {
        if (roomInfo.setState(spaceId, CHILD, EMPTY, childId).isFailure) return Removal.Failed
        // The child may also name the space as its parent, which is enough for it to stay listed.
        val state = state(childId, members = false).getOrNull() ?: return Removal.Done
        val parent = state.firstOrNull { it.type == PARENT && it.stateKey == spaceId }
        if (parent == null || (parent.content["via"] as? JsonArray).isNullOrEmpty()) return Removal.Done
        val levels = RoomInfo.parse(childId, state).powerLevels
        if (!levels.canSetState(me, PARENT)) return Removal.ParentKept
        return if (roomInfo.setState(childId, PARENT, EMPTY, spaceId).isSuccess) Removal.Done else Removal.ParentKept
    }

    private suspend fun addChild(
        me: String,
        spaceId: String,
        childId: String,
    ): Boolean {
        val via =
            state(childId, members = true)
                .map { SpaceEdits.viaServers(me, RoomInfo.parse(childId, it)) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(SpaceEdits.serverOf(me))
        val content = buildJsonObject { put("via", JsonArray(via.map(::JsonPrimitive))) }
        return roomInfo.setState(spaceId, CHILD, content, childId).isSuccess
    }

    /** The room's state as gomuks holds it, with the members it already has (never fetched). */
    private suspend fun state(
        roomId: String,
        members: Boolean,
    ): Result<List<Event>> {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                if (members) put("include_members", JsonPrimitive(true))
            }
        return exec.exec("get_room_state", params, ExecMode.Read).toResult().mapCatching {
            GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), it)
        }
    }

    companion object {
        const val CHILD = "m.space.child"
        const val PARENT = "m.space.parent"
        private val EMPTY = JsonObject(emptyMap())
    }
}
