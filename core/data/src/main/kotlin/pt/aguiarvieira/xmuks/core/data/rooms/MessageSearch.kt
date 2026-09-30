package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode

/** What to look for, and where. */
data class SearchQuery(
    val text: String,
    /** Only this room; everywhere when null. */
    val roomId: String? = null,
    /** Ask the homeserver (unencrypted rooms, all history) instead of gomuks' own store. */
    val onServer: Boolean = false,
    /** Newest first, instead of best match first. */
    val byTime: Boolean = true,
)

/** One page of hits, and the token for the next (null when there's no more). */
data class SearchPage(
    val hits: List<FoundEvent>,
    val next: String?,
)

/**
 * Message search: gomuks' full-text index of what it holds (`search_local`, encrypted rooms too),
 * or the homeserver's `/search` (`search_server`).
 */
class MessageSearch(
    private val exec: ExecClient,
    private val found: FoundEvents,
) {
    suspend fun page(
        query: SearchQuery,
        next: String? = null,
        limit: Int = PAGE,
    ): Result<SearchPage> {
        val term = if (query.onServer) query.text.trim() else ftsQuery(query.text)
        if (term.isEmpty()) return Result.success(SearchPage(emptyList(), null))
        val params =
            buildJsonObject {
                put("search_term", JsonPrimitive(term))
                put("limit", JsonPrimitive(limit))
                query.roomId?.let { put("room_ids", JsonArray(listOf(JsonPrimitive(it)))) }
                if (query.byTime) put("sort_by_time", JsonPrimitive(true))
                next?.let { put("next_batch", JsonPrimitive(it)) }
            }
        val command = if (query.onServer) "search_server" else "search_local"
        return exec.exec(command, params, ExecMode.Read).toResult().mapCatching { data ->
            val page = data as? JsonObject
            SearchPage(
                hits = found.resolve(found.decode(page?.get("events"))),
                next = (page?.get("next_batch") as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() },
            )
        }
    }

    private companion object {
        const val PAGE = 50
    }
}

/**
 * What the user typed, as an SQLite FTS5 query that can't be a syntax error: every word quoted
 * (all must match), the last one a prefix so results come while typing.
 */
internal fun ftsQuery(text: String): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words
        .mapIndexed { i, word ->
            val quoted = "\"" + word.replace("\"", "\"\"") + "\""
            if (i == words.lastIndex) "$quoted*" else quoted
        }.joinToString(" ")
}
