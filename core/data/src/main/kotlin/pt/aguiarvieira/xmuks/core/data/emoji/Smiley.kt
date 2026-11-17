package pt.aguiarvieira.xmuks.core.data.emoji

/** A text smiley in a message ([start] until [end]) and the emoji it stands for. */
data class Smiley(
    val start: Int,
    val end: Int,
    val emoji: String,
)

/**
 * The smiley that ends at [end] in [text], if there is one. It must be a whole word, running from
 * the start of the text or a whitespace up to [end]. So `http://` and `(see 8)` stay as they are,
 * and only a smiley written on its own is swapped.
 */
fun smileyEndingAt(
    text: String,
    end: Int,
): Smiley? {
    if (end !in 1..text.length) return null
    var start = end
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    val emoji = SMILEYS[text.substring(start, end)] ?: return null
    return Smiley(start, end, emoji)
}

/**
 * Text smileys and the emoji they become. Based on emojibase's `emoticon` field (what Element's
 * "replace plain text emoji" uses), minus its odd ones (`:j`, `8#`, `:l` for 🤔). Ones a list or a
 * word could be (`B)`, `8)`, `D:`, `^^`) are left out. Japanese-style kaomoji are added.
 */
private val SMILEYS: Map<String, String> =
    buildMap {
        fun put(
            emoji: String,
            vararg smileys: String,
        ) = smileys.forEach { put(it, emoji) }

        put("🙂", ":)", ":-)", "(:", "=)")
        put("😄", ":D", ":-D", "=D")
        put("😆", "xD", "XD")
        put("😂", ":')", ":'-)")
        put("🤣", ":'D")
        put("😉", ";)", ";-)")
        put("😊", ":>", "^_^", "^.^", "^-^")
        put("😅", "^_^;", "^^;")
        put("😇", "O:)", "o:)", "O:-)", "0:)")
        put("😘", ":*", ":-*")
        put("😛", ":P", ":p", ":-P", ":-p", "=P", "=p")
        put("😜", ";P", ";p", ";-P", ";-p")
        put("😝", "xP", "XP")
        put("😐", ":|", ":-|")
        put("😑", "-_-", "-.-")
        put("😓", "-_-;", "-_-'")
        put("😕", ":/", ":-/", ":\\", ":-\\")
        put("🙁", ":(", ":-(", "=(")
        put("😢", ":'(", ":'-(")
        put("😭", "T_T", "T.T", "TT_TT", ";_;")
        put("😮", ":O", ":o", ":-O", ":-o")
        put("😳", ":$", "O_O", "o_o", "0_0")
        put("🤨", "o_O", "O_o", "o.O", "O.o", "0_o", "o_0")
        put("😧", ":S", ":s")
        put("😣", ">_<", ">.<")
        put("😵", "x_x", "X_X")
        put("🤩", "*_*")
        put("😔", "u_u")
        put("😎", "B-)", "8-)")
        put("🤓", ":B")
        put("😠", ">:(", ">:-(")
        put("😡", ":@")
        put("😈", ">:)", ">:-)")
        put("😺", ":3")
        put("❤️", "<3")
        put("💔", "</3")
        put("🙌", "\\o/")
        put("👋", "o/")
    }
