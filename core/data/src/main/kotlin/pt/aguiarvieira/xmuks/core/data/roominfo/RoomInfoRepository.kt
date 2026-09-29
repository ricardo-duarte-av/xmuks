package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** What moderation does to a member (`set_membership`). */
enum class MembershipAction(
    val wire: String,
) {
    Invite("invite"),
    Kick("kick"),
    Ban("ban"),
    Unban("unban"),
}

/**
 * A room's state and members (`get_room_state` with its members), and changing them: state events
 * (`set_state`), membership (`set_membership`), leaving; and rooms we're not in — their summary,
 * joining and knocking.
 */
class RoomInfoRepository(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val ingestor: SyncIngestor,
) {
    private val dao = database.roomListDao()

    val me: Flow<String?> = dao.meta().map { it?.userId }.distinctUntilChanged()

    /** Once at the start, then whenever a sync changes the room's state: time to (re)load it. */
    fun stateChanges(roomId: String): Flow<Unit> =
        ingestor.stateChanged
            .filter { it == roomId }
            .map { }
            .onStart { emit(Unit) }

    /**
     * The room's current state with its members. Until gomuks has the whole member list, it's
     * fetched from the server first (once: gomuks remembers it has it).
     */
    suspend fun load(roomId: String): Result<RoomInfo> {
        val complete = dao.room(roomId).first()?.hasMemberList == true
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("include_members", JsonPrimitive(true))
                put("fetch_members", JsonPrimitive(!complete))
            }
        return exec.exec("get_room_state", params, ExecMode.Read).toResult().mapCatching { data ->
            val events = GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), data)
            RoomInfo.parse(roomId, events)
        }
    }

    suspend fun setState(
        roomId: String,
        type: String,
        content: JsonObject,
        stateKey: String = "",
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(type))
                put("state_key", JsonPrimitive(stateKey))
                put("content", content)
            }
        return exec.exec("set_state", params, ExecMode.Write).toResult().map { }
    }

    suspend fun setMembership(
        roomId: String,
        userId: String,
        action: MembershipAction,
        reason: String? = null,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("action", JsonPrimitive(action.wire))
                put("room_id", JsonPrimitive(roomId))
                put("user_id", JsonPrimitive(userId))
                reason?.takeIf { it.isNotBlank() }?.let { put("reason", JsonPrimitive(it)) }
            }
        return exec.exec("set_membership", params, ExecMode.Write).toResult().map { }
    }

    suspend fun leave(
        roomId: String,
        reason: String? = null,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                reason?.takeIf { it.isNotBlank() }?.let { put("reason", JsonPrimitive(it)) }
            }
        return exec.exec("leave_room", params, ExecMode.Write).toResult().map { }
    }

    /** A room we may not be in (`get_room_summary`), found by ID or alias through [via] servers. */
    suspend fun summary(
        roomIdOrAlias: String,
        via: List<String> = emptyList(),
    ): Result<RoomPreview> =
        exec.exec("get_room_summary", target(roomIdOrAlias, via), ExecMode.Read).toResult().mapCatching {
            RoomPreview.parse(it as? JsonObject ?: error("No summary"))
        }

    /** Joins; the result is the room's ID. */
    suspend fun join(
        roomIdOrAlias: String,
        via: List<String> = emptyList(),
        reason: String? = null,
    ): Result<String> =
        exec.exec("join_room", target(roomIdOrAlias, via, reason), ExecMode.Write).toResult().mapCatching {
            (it as? JsonObject)?.str("room_id") ?: roomIdOrAlias
        }

    suspend fun knock(
        roomIdOrAlias: String,
        via: List<String> = emptyList(),
        reason: String? = null,
    ): Result<Unit> = exec.exec("knock_room", target(roomIdOrAlias, via, reason), ExecMode.Write).toResult().map { }

    private fun target(
        roomIdOrAlias: String,
        via: List<String>,
        reason: String? = null,
    ) = buildJsonObject {
        put("room_id_or_alias", JsonPrimitive(roomIdOrAlias))
        // A room ID alone can't be looked up remotely: its own server (older room versions have one) at least.
        val servers =
            via.ifEmpty {
                listOfNotNull(roomIdOrAlias.takeIf { it.startsWith("!") }?.substringAfter(':', "")?.ifEmpty { null })
            }
        if (servers.isNotEmpty()) put("via", buildJsonArray { servers.forEach { add(JsonPrimitive(it)) } })
        reason?.takeIf { it.isNotBlank() }?.let { put("reason", JsonPrimitive(it)) }
    }
}

/** What `get_room_summary` tells about a room before joining it. */
data class RoomPreview(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarMxc: String?,
    val canonicalAlias: String?,
    val joinedMembers: Int,
    val joinRule: String,
    val worldReadable: Boolean,
    val isSpace: Boolean,
    val encrypted: Boolean,
    /** Ours in the room, if any: `invite`, `knock`, `join`, `leave`, `ban`. */
    val membership: String?,
) {
    val canKnock: Boolean get() = joinRule == "knock" || joinRule == "knock_restricted"

    companion object {
        fun parse(json: JsonObject) =
            RoomPreview(
                roomId = json.str("room_id") ?: error("No room ID"),
                name = json.str("name")?.takeIf { it.isNotBlank() },
                topic = json.str("topic")?.takeIf { it.isNotBlank() },
                avatarMxc = json.str("avatar_url")?.takeIf { it.isNotBlank() },
                canonicalAlias = json.str("canonical_alias"),
                joinedMembers = (json["num_joined_members"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                joinRule = json.str("join_rule") ?: "public",
                worldReadable = (json["world_readable"] as? JsonPrimitive)?.content == "true",
                isSpace = json.str("room_type") == "m.space",
                encrypted =
                    (json.str("encryption") ?: json.str("im.nheko.summary.encryption")) != null,
                membership = json.str("membership"),
            )
    }
}
