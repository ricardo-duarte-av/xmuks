package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase

/** Someone or some room a message can mention: shown by name and avatar, linked by [link]. */
data class MentionTarget(
    /** A user ID, or a room's alias (else its ID). */
    val id: String,
    val name: String,
    val avatarMxc: String?,
    val room: Boolean,
) {
    /** What goes in the message: a matrix.to link, which gomuks turns into a mention. */
    val link: String get() = "[${name.replace("[", "").replace("]", "")}](https://matrix.to/#/$id)"
}

/** What can be mentioned: a room's members (their names there), and the rooms we're in. */
class MentionTargets(
    private val roomInfo: RoomInfoRepository,
    database: XmuksDatabase,
) {
    private val dao = database.roomListDao()

    /** [roomId]'s joined members, with their names and avatars in that room. */
    suspend fun members(roomId: String): List<MentionTarget> =
        roomInfo
            .load(roomId)
            .getOrNull()
            ?.members
            .orEmpty()
            .filter { it.membership == Membership.Join }
            .map {
                MentionTarget(
                    it.userId,
                    it.displayName?.takeIf(String::isNotBlank) ?: it.userId,
                    it.avatarMxc,
                    room = false
                )
            }

    /** Every room we're in, most recently active first. */
    fun rooms(): Flow<List<MentionTarget>> =
        dao.roomsByRecency().map { rooms ->
            rooms
                .filter { !it.isSpace }
                .map {
                    MentionTarget(
                        it.canonicalAlias ?: it.roomId,
                        it.name ?: it.canonicalAlias ?: it.roomId,
                        it.avatar,
                        room = true
                    )
                }
        }
}

/** The word being typed at the cursor, when it's a mention: where it is, and what follows its `@` or `#`. */
data class MentionQuery(
    val start: Int,
    val end: Int,
    val room: Boolean,
    val text: String,
)

/**
 * The mention being typed at [cursor] in [text]: an `@` (people) or `#` (rooms) at the start of the
 * message or after a space, and whatever follows it up to the cursor.
 */
fun mentionQueryAt(
    text: String,
    cursor: Int,
): MentionQuery? {
    if (cursor !in 1..text.length) return null
    var start = cursor
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    val word = text.substring(start, cursor)
    val trigger = word.firstOrNull()
    // Already a link we inserted, or markdown: leave it.
    val mention = (trigger == '@' || trigger == '#') && !word.contains('(') && !word.contains('[')
    return if (mention) MentionQuery(start, cursor, trigger == '#', word.drop(1)) else null
}

/** [targets] matching [query]: names starting with it first, then IDs starting with it, then the rest. */
fun List<MentionTarget>.matching(
    query: String,
    limit: Int = 20,
): List<MentionTarget> {
    val q = query.lowercase()

    fun rank(t: MentionTarget): Int {
        val name = t.name.lowercase()
        val id = t.id.lowercase().drop(1)
        return when {
            name.startsWith(q) -> 0
            id.startsWith(q) -> 1
            name.contains(q) || id.contains(q) -> 2
            else -> NO_MATCH
        }
    }
    return asSequence()
        .map { it to rank(it) }
        .filter { it.second != NO_MATCH }
        .sortedBy { it.second }
        .map { it.first }
        .take(limit)
        .toList()
}

private const val NO_MATCH = 3
