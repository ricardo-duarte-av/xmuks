package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository.Companion.directoryResult
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode

/** What a space's `m.space.child` event says of a child: the servers to join it through, whether it's suggested. */
data class SpaceLink(
    val via: List<String>,
    val suggested: Boolean,
)

/**
 * One page of a space's direct children; [nextBatch] null on the last. [links] come from the
 * space's own entry, on the first page only: keep them for the pages after.
 */
data class SpaceHierarchyPage(
    val rooms: List<DirectoryRoom>,
    val links: Map<String, SpaceLink>,
    val nextBatch: String?,
)

/**
 * A space's children, rooms we're not in included (`get_space_hierarchy`, one level down). The
 * server leaves out rooms we may not see; [DirectoryUnsupported] means gomuks is too old.
 */
class SpaceHierarchyRepository(
    private val exec: ExecClient,
    database: XmuksDatabase,
) {
    private val dao = database.roomListDao()

    /** The IDs of [spaceId]'s children we're in, rooms and subspaces alike (live). */
    fun joinedChildren(spaceId: String): Flow<Set<String>> =
        dao.spaceChildren(spaceId).map { rows -> rows.mapTo(HashSet()) { it.roomId } }.distinctUntilChanged()

    suspend fun page(
        spaceId: String,
        from: String?,
    ): Result<SpaceHierarchyPage> =
        exec.exec(GET, params(spaceId, from), ExecMode.Read).directoryResult().mapCatching {
            parse(spaceId, it as? JsonObject ?: error("No hierarchy"))
        }

    companion object {
        private const val GET = "get_space_hierarchy"
        private const val PAGE_SIZE = 50
        private const val CHILD = "m.space.child"

        internal fun params(
            spaceId: String,
            from: String?,
        ) = buildJsonObject {
            put("room_id", JsonPrimitive(spaceId))
            put("limit", JsonPrimitive(PAGE_SIZE))
            put("max_depth", JsonPrimitive(1))
            from?.let { put("from", JsonPrimitive(it)) }
        }

        /** The page's rooms but the space itself, and the `m.space.child` links the page carries. */
        internal fun parse(
            spaceId: String,
            json: JsonObject,
        ): SpaceHierarchyPage {
            val entries = (json["rooms"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            // Only the space's own entry: a subspace's children are a level further down.
            val links =
                entries
                    .filter { it.str("room_id") == spaceId }
                    .flatMap { (it["children_state"] as? JsonArray).orEmpty() }
                    .mapNotNull { it as? JsonObject }
                    .filter { it.str("type") == CHILD }
                    .mapNotNull { event ->
                        val childId = event.str("state_key") ?: return@mapNotNull null
                        val content = event["content"] as? JsonObject ?: return@mapNotNull null
                        val via =
                            (content["via"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
                        // A child event without servers is a removed child.
                        if (via.isEmpty()) return@mapNotNull null
                        childId to SpaceLink(via, (content["suggested"] as? JsonPrimitive)?.content == "true")
                    }.toMap()
            val rooms =
                entries
                    .mapNotNull { RoomDirectoryRepository.parseRoom(it) }
                    .filter { it.roomId != spaceId }
            return SpaceHierarchyPage(rooms, links, json.str("next_batch")?.takeIf { it.isNotEmpty() })
        }
    }
}
