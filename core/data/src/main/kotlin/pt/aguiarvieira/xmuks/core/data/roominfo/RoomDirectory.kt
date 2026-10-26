package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import java.io.IOException

/** A room as our server's directory lists it. */
data class DirectoryRoom(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarMxc: String?,
    val alias: String?,
    val members: Int,
    val joinRule: String,
    val isSpace: Boolean,
)

/** One page of the directory; [nextBatch] null on the last. */
data class DirectoryPage(
    val rooms: List<DirectoryRoom>,
    val nextBatch: String?,
    val total: Int?,
)

/** gomuks predates the directory's commands. */
class DirectoryUnsupported : IOException("gomuks is too old for the room directory")

/**
 * Our server's public room directory (`get_room_directory`), and whether a room is listed in it
 * (`get_room_visibility`, `publish_room`).
 */
class RoomDirectoryRepository(
    private val exec: ExecClient,
) {
    suspend fun page(
        search: String,
        spaces: Boolean,
        since: String?,
    ): Result<DirectoryPage> =
        exec.exec(GET, pageParams(search, spaces, since), ExecMode.Read).directoryResult().mapCatching {
            parsePage(it as? JsonObject ?: error("No directory"))
        }

    /** Whether [roomId] is listed. */
    suspend fun listed(roomId: String): Result<Boolean> =
        exec.exec(VISIBILITY, roomParams(roomId), ExecMode.Read).directoryResult().mapCatching {
            (it as? JsonObject)?.str("visibility") == PUBLIC
        }

    suspend fun setListed(
        roomId: String,
        listed: Boolean,
    ): Result<Unit> = exec.exec(PUBLISH, publishParams(roomId, listed), ExecMode.Write).directoryResult().map { }

    companion object {
        private const val GET = "get_room_directory"
        private const val VISIBILITY = "get_room_visibility"
        private const val PUBLISH = "publish_room"
        private const val PUBLIC = "public"
        private const val PAGE_SIZE = 30

        internal fun pageParams(
            search: String,
            spaces: Boolean,
            since: String?,
        ) = buildJsonObject {
            put("limit", JsonPrimitive(PAGE_SIZE))
            since?.let { put("since", JsonPrimitive(it)) }
            val term = search.trim()
            if (term.isNotEmpty() || spaces) {
                put(
                    "filter",
                    buildJsonObject {
                        if (term.isNotEmpty()) put("generic_search_term", JsonPrimitive(term))
                        if (spaces) put("room_types", buildJsonArray { add(JsonPrimitive("m.space")) })
                    },
                )
            }
        }

        internal fun publishParams(
            roomId: String,
            listed: Boolean,
        ) = buildJsonObject {
            put("room_id", JsonPrimitive(roomId))
            put("visibility", JsonPrimitive(if (listed) PUBLIC else "private"))
        }

        private fun roomParams(roomId: String) = buildJsonObject { put("room_id", JsonPrimitive(roomId)) }

        internal fun parsePage(json: JsonObject): DirectoryPage =
            DirectoryPage(
                rooms =
                    (json["chunk"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::parseRoom) },
                nextBatch = json.str("next_batch")?.takeIf { it.isNotEmpty() },
                total = (json["total_room_count_estimate"] as? JsonPrimitive)?.content?.toIntOrNull(),
            )

        private fun parseRoom(json: JsonObject): DirectoryRoom? =
            DirectoryRoom(
                roomId = json.str("room_id") ?: return null,
                name = json.str("name")?.takeIf { it.isNotBlank() },
                topic = json.str("topic")?.takeIf { it.isNotBlank() },
                avatarMxc = json.str("avatar_url")?.takeIf { it.isNotBlank() },
                alias = json.str("canonical_alias")?.takeIf { it.isNotBlank() },
                members = (json["num_joined_members"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                joinRule = json.str("join_rule") ?: PUBLIC,
                isSpace = json.str("room_type") == "m.space",
            )

        /** As `toResult`, but an older gomuks' "unknown command" is [DirectoryUnsupported]. */
        internal fun ExecResult.directoryResult(): Result<JsonElement> =
            when (this) {
                is ExecResult.Ok -> {
                    Result.success(data)
                }

                is ExecResult.CommandError -> {
                    Result.failure(
                        if (message.contains("unknown command")) DirectoryUnsupported() else IOException(message),
                    )
                }

                is ExecResult.NetworkError -> {
                    Result.failure(cause)
                }
            }
    }
}
