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

/** The emoji [smiley] stands for, when it's one. */
fun emojiForSmiley(smiley: String): String? = SMILEYS[smiley]

/**
 * Whether [smiley] can be swapped as soon as it's typed. It can when it ends in punctuation (`:)`,
 * `-_-`). Ending in a letter or digit (`:p`, `xD`, `<3`), it could be the start of a word, a number
 * or a `:shortcode:` (`:pray:`), so it waits for a space or for the message to be sent.
 */
fun isInstantSmiley(smiley: String): Boolean = smiley.lastOrNull()?.isLetterOrDigit() == false

/**
 * Text smileys and the emoji they become. Based on emojibase's `emoticon` field (what Element's
 * "replace plain text emoji" uses), minus its odd ones (`:j`, `8#`, `:l` for 🤔). Ones a list or a
 * word could be (`B)`, `8)`, `D:`, `^^`) are left out, and so is `(:`, which would be swapped too
 * soon in `(:memo:)`. Japanese-style kaomoji are added.
 */
private val SMILEYS: Map<String, String> =
    buildMap<String, String> {
        fun smileys(
            emoji: String,
            vararg texts: String,
        ) {
            for (text in texts) put(text, emoji)
        }

        smileys("🙂", ":)", ":-)", "=)")
        smileys("😄", ":D", ":-D", "=D")
        smileys("😆", "xD", "XD")
        smileys("😂", ":')", ":'-)")
        smileys("🤣", ":'D")
        smileys("😉", ";)", ";-)")
        smileys("😊", ":>", "^_^", "^.^", "^-^")
        smileys("😅", "^_^;", "^^;")
        smileys("😇", "O:)", "o:)", "O:-)", "0:)")
        smileys("😘", ":*", ":-*")
        smileys("😛", ":P", ":p", ":-P", ":-p", "=P", "=p")
        smileys("😜", ";P", ";p", ";-P", ";-p")
        smileys("😝", "xP", "XP")
        smileys("😐", ":|", ":-|")
        smileys("😑", "-_-", "-.-")
        smileys("😓", "-_-;", "-_-'")
        smileys("😕", ":/", ":-/", ":\\", ":-\\")
        smileys("🙁", ":(", ":-(", "=(")
        smileys("😢", ":'(", ":'-(")
        smileys("😭", "T_T", "T.T", "TT_TT", ";_;")
        smileys("😮", ":O", ":o", ":-O", ":-o")
        smileys("😳", ":$", "O_O", "o_o", "0_0")
        smileys("🤨", "o_O", "O_o", "o.O", "O.o", "0_o", "o_0")
        smileys("😧", ":S", ":s")
        smileys("😣", ">_<", ">.<")
        smileys("😵", "x_x", "X_X")
        smileys("🤩", "*_*")
        smileys("😔", "u_u")
        smileys("😎", "B-)", "8-)")
        smileys("🤓", ":B")
        smileys("😠", ">:(", ">:-(")
        smileys("😡", ":@")
        smileys("😈", ">:)", ">:-)")
        smileys("😺", ":3")
        smileys("❤️", "<3")
        smileys("💔", "</3")
        smileys("🙌", "\\o/")
        smileys("👋", "o/")
    }
