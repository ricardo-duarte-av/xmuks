package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.obj
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException

/**
 * Us and other people: our DM with someone (found through `m.direct`, or started), who we ignore
 * (`m.ignored_user_list`), and the rooms we share with someone.
 */
class Contacts(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val rooms: RoomListRepository,
) {
    private val dao = database.roomListDao()

    /** Everyone we ignore. */
    val ignored: Flow<Set<String>> =
        dao
            .accountData("", IGNORED)
            .map { json -> parse(json)?.obj(IGNORED_USERS)?.keys.orEmpty() }
            .distinctUntilChanged()

    /**
     * A DM with [userId] that we're in: one of the rooms `m.direct` lists for them (the most
     * recently active), or failing that one gomuks considers a DM with them.
     */
    suspend fun directRoom(userId: String): String? {
        val direct = parse(dao.accountData("", DIRECT).first())
        val listed = (direct?.get(userId) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return listed.takeIf { it.isNotEmpty() }?.let { dao.latestJoined(it) } ?: dao.dmWith(userId)
    }

    /**
     * Starts a DM with [userId] (as gomuks web does: a trusted private chat, encrypted when they
     * have devices to encrypt for; gomuks adds it to `m.direct`). The result is the new room, once
     * it has synced to us (or after a while, regardless).
     */
    suspend fun startDirectChat(userId: String): Result<String> {
        val encrypt = hasDevices(userId)
        val params =
            buildJsonObject {
                put("is_direct", JsonPrimitive(true))
                put("preset", JsonPrimitive("trusted_private_chat"))
                put("invite", buildJsonArray { add(JsonPrimitive(userId)) })
                put(
                    "initial_state",
                    buildJsonArray {
                        if (encrypt) {
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("m.room.encryption"))
                                    put("state_key", JsonPrimitive(""))
                                    put("content", buildJsonObject { put("algorithm", JsonPrimitive(MEGOLM)) })
                                },
                            )
                        }
                    },
                )
            }
        return exec.exec("create_room", params, ExecMode.Write).toResult().mapCatching { data ->
            val roomId = (data as? JsonObject)?.str("room_id") ?: throw IOException("No room ID")
            withTimeoutOrNull(ARRIVAL_TIMEOUT_MS) { rooms.room(roomId).filterNotNull().first() }
            roomId
        }
    }

    /** Ignores (or stops ignoring) [userId], keeping the rest of the list and anything else in it. */
    suspend fun setIgnored(
        userId: String,
        ignore: Boolean,
    ): Result<Unit> {
        val current = parse(dao.accountData("", IGNORED).first()) ?: JsonObject(emptyMap())
        val users = current.obj(IGNORED_USERS).orEmpty().toMutableMap()
        if (ignore) users[userId] = JsonObject(emptyMap()) else users.remove(userId)
        val params =
            buildJsonObject {
                put("type", JsonPrimitive(IGNORED))
                put("content", JsonObject(current + (IGNORED_USERS to JsonObject(users))))
            }
        return exec.exec("set_account_data", params, ExecMode.Write).toResult().map { }
    }

    /** The rooms we share with [userId] (their server has to support it: MSC2666). */
    suspend fun mutualRooms(userId: String): Result<List<RoomSummary>> {
        val params = buildJsonObject { put("user_id", JsonPrimitive(userId)) }
        return exec.exec("get_mutual_rooms", params, ExecMode.Read).toResult().mapCatching { data ->
            val ids =
                ((data as? JsonObject)?.get("joined") as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            rooms.rooms(ids)
        }
    }

    /** Whether [userId] has any device to encrypt for (`track_user_devices`); false when unknown. */
    private suspend fun hasDevices(userId: String): Boolean {
        val params = buildJsonObject { put("user_id", JsonPrimitive(userId)) }
        val info = exec.exec("track_user_devices", params, ExecMode.Read).toResult().getOrNull() as? JsonObject
        return (info?.get("devices") as? JsonArray)?.isNotEmpty() == true
    }

    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { GomuksJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private companion object {
        const val DIRECT = "m.direct"
        const val IGNORED = "m.ignored_user_list"
        const val IGNORED_USERS = "ignored_users"
        const val MEGOLM = "m.megolm.v1.aes-sha2"
        const val ARRIVAL_TIMEOUT_MS = 15_000L
    }
}
