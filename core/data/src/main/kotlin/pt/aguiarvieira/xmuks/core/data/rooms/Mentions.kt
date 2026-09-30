package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode

/** Which notifications: those that highlighted (mentions, keywords), or everything that notified. */
enum class MentionKind(
    /** gomuks' unread type bits. */
    val unreadType: Int,
) {
    Mentions(HIGHLIGHT),
    All(NOTIFY),
}

private const val NOTIFY = 0b0010
private const val HIGHLIGHT = 0b0100

/** Past notifications, newest first, from gomuks' own record of them (`get_mentions`). */
class Mentions(
    private val exec: ExecClient,
    private val found: FoundEvents,
) {
    /** Up to [limit] notifications older than [before] (a timestamp), optionally in one room. */
    suspend fun page(
        kind: MentionKind,
        before: Long,
        limit: Int = PAGE,
        roomId: String? = null,
    ): Result<List<FoundEvent>> {
        val params =
            buildJsonObject {
                put("max_timestamp", JsonPrimitive(before))
                put("type", JsonPrimitive(kind.unreadType))
                put("limit", JsonPrimitive(limit))
                roomId?.let { put("room_id", JsonPrimitive(it)) }
            }
        return exec
            .exec("get_mentions", params, ExecMode.Read)
            .toResult()
            .mapCatching { found.resolve(found.decode(it)) }
    }

    private companion object {
        const val PAGE = 30
    }
}
