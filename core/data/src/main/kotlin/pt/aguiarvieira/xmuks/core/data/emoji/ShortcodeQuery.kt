package pt.aguiarvieira.xmuks.core.data.emoji

/** An emoji `:shortcode` at the cursor: where it is (from its opening `:`), and what follows that `:`. */
data class ShortcodeQuery(
    val start: Int,
    val end: Int,
    val text: String,
)

/**
 * The emoji shortcode being typed at [cursor] in [text]: a `:` at the start of the message or after
 * a space (or anything but a letter or digit, so `📝:rocket` works but `10:30` and `https:` don't),
 * then at least [minLength] shortcode characters up to the cursor.
 */
fun shortcodeQueryAt(
    text: String,
    cursor: Int,
    minLength: Int = MIN_QUERY,
): ShortcodeQuery? {
    if (cursor !in 1..text.length) return null
    var start = cursor
    while (start > 0 && text[start - 1].isShortcodeChar()) start--
    val colon = start - 1
    if (colon < 0 || text[colon] != ':') return null
    if (colon > 0 && text[colon - 1].isLetterOrDigit()) return null
    val query = text.substring(start, cursor)
    return if (query.length >= minLength) ShortcodeQuery(colon, cursor, query) else null
}

/**
 * A shortcode just closed at [cursor]: `:memo:` with the cursor right after its second `:`. Its
 * [ShortcodeQuery.end] is the cursor, so replacing start..end swaps the whole placeholder.
 */
fun closedShortcodeAt(
    text: String,
    cursor: Int,
): ShortcodeQuery? {
    if (cursor !in 2..text.length || text[cursor - 1] != ':') return null
    val open = shortcodeQueryAt(text, cursor - 1, minLength = 1) ?: return null
    return open.copy(end = cursor)
}

/**
 * [items] whose shortcodes match [query], each with the shortcode that matched best: exact matches
 * first, then those starting with it, then those containing it; ties keep [items]' order.
 */
fun <T> List<T>.matchingShortcode(
    query: String,
    codes: (T) -> List<String>,
    limit: Int = MAX_HINTS,
): List<Pair<T, String>> {
    val q = query.lowercase()
    if (q.isEmpty()) return emptyList()

    fun rank(code: String): Int {
        val c = code.lowercase()
        return when {
            c == q -> 0
            c.startsWith(q) -> 1
            c.contains(q) -> 2
            else -> NO_MATCH
        }
    }
    return asSequence()
        .mapNotNull { item ->
            val (code, best) = codes(item).map { it to rank(it) }.minByOrNull { it.second } ?: return@mapNotNull null
            if (best == NO_MATCH) null else Triple(item, code, best)
        }.sortedBy { it.third }
        .take(limit)
        .map { it.first to it.second }
        .toList()
}

private fun Char.isShortcodeChar() = isLetterOrDigit() || this == '_' || this == '-' || this == '+'

private const val MIN_QUERY = 2
private const val MAX_HINTS = 20
private const val NO_MATCH = 3
