package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * [json] full screen, as [CodeViewer] shows code: monospace, line numbers, wrap and copy, its
 * syntax in the colours of the [MaterialTheme] around it (a room's own, where it has them).
 */
@Composable
fun JsonViewer(
    json: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val block = remember(json) { HtmlBlock.Code(json, jsonTokens(json), language = "json") }
    val text = remember(block, scheme) { jsonHighlighted(block, scheme) }
    CodeViewer(block, text, codeStyle(MaterialTheme.typography.bodyLarge), theme = null, onDismiss, title)
}

/** [block]'s JSON tokens styled from [scheme]: keys, strings, numbers, literals, punctuation. */
private fun jsonHighlighted(
    block: HtmlBlock.Code,
    scheme: ColorScheme,
): AnnotatedString {
    val styles =
        mapOf(
            KEY to SpanStyle(color = scheme.primary),
            STRING to SpanStyle(color = scheme.tertiary),
            NUMBER to SpanStyle(color = scheme.secondary),
            LITERAL to SpanStyle(color = scheme.secondary, fontWeight = FontWeight.Bold),
            PUNCTUATION to SpanStyle(color = scheme.onSurfaceVariant),
        )
    return buildAnnotatedString {
        append(block.code)
        block.tokens.forEach { token -> styles[token.cls]?.let { addStyle(it, token.start, token.end) } }
    }
}

/**
 * [json]'s tokens, in Chroma's classes as gomuks' code blocks have them: keys (`nt`), strings
 * (`s2`), numbers (`m`), `true`/`false`/`null` (`kc`), punctuation (`p`). Whitespace has none.
 * Lenient: whatever isn't JSON is left plain.
 */
internal fun jsonTokens(json: String): List<CodeToken> {
    val tokens = mutableListOf<CodeToken>()
    var i = 0
    while (i < json.length) {
        val c = json[i]
        val end =
            when {
                c == '"' -> stringEnd(json, i)
                c == '-' || c.isDigit() -> json.firstIndexFrom(i) { it !in NUMBER_CHARS }
                c.isLetter() -> json.firstIndexFrom(i) { !it.isLetter() }
                c in PUNCTUATION_CHARS -> i + 1
                else -> -1
            }
        if (end < 0) {
            i++
            continue
        }
        val word = json.substring(i, end)
        val cls =
            when {
                c == '"' -> if (json.nextNonSpace(end) == ':') KEY else STRING
                c == '-' || c.isDigit() -> NUMBER
                c.isLetter() -> LITERAL.takeIf { word in LITERALS }
                else -> PUNCTUATION
            }
        if (cls != null) tokens += CodeToken(i, end, cls)
        i = end
    }
    return tokens
}

/** Past the string opening at [start]'s closing quote (escapes skipped), or the end of [json]. */
private fun stringEnd(
    json: String,
    start: Int,
): Int {
    var i = start + 1
    while (i < json.length) {
        when (json[i]) {
            '\\' -> i++
            '"' -> return i + 1
        }
        i++
    }
    return json.length
}

private fun String.firstIndexFrom(
    from: Int,
    predicate: (Char) -> Boolean,
): Int = (from until length).firstOrNull { predicate(this[it]) } ?: length

private fun String.nextNonSpace(from: Int): Char? = getOrNull(firstIndexFrom(from) { !it.isWhitespace() })

private const val KEY = "nt"
private const val STRING = "s2"
private const val NUMBER = "m"
private const val LITERAL = "kc"
private const val PUNCTUATION = "p"
private val LITERALS = setOf("true", "false", "null")
private const val NUMBER_CHARS = "-+.0123456789eE"
private const val PUNCTUATION_CHARS = "{}[],:"
