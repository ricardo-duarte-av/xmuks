package pt.aguiarvieira.xmuks.core.richtext

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A run of highlighted code: [start] until [end] is a Chroma token class ([cls], e.g. `kd`). */
data class CodeToken(
    val start: Int,
    val end: Int,
    val cls: String,
)

/**
 * A `<pre>` as gomuks sends it: Chroma's highlighted lines (`span.line > span.ln + span.cl >
 * span.<token>`, the line numbers left empty) or a plain `<pre><code class="language-…">`. The
 * text is the block's own; the tokens are where each class applies.
 */
internal fun codeBlockOf(pre: Element): HtmlBlock.Code {
    val text = StringBuilder()
    val tokens = mutableListOf<CodeToken>()

    fun walk(
        node: Node,
        cls: String?,
    ) {
        when (node) {
            is TextNode -> {
                val start = text.length
                text.append(node.wholeText)
                if (cls != null && text.length > start) tokens += CodeToken(start, text.length, cls)
            }

            is Element -> {
                val own = node.classNames().firstOrNull { it !in STRUCTURE && !it.startsWith(LANGUAGE) }
                node.childNodes().forEach { walk(it, own ?: cls) }
            }
        }
    }
    pre.childNodes().forEach { walk(it, null) }
    val code = text.trimEnd('\n').toString()
    val language =
        pre
            .select("code")
            .flatMap { it.classNames() }
            .firstOrNull { it.startsWith(LANGUAGE) }
            ?.removePrefix(LANGUAGE)
    return HtmlBlock.Code(
        code = code,
        tokens = tokens.mapNotNull { t -> t.takeIf { it.start < code.length }?.copy(end = minOf(t.end, code.length)) },
        language = language,
    )
}

/** Chroma's structural classes, and whitespace: no colour of their own. */
private val STRUCTURE = setOf("chroma", "line", "cl", "ln", "w")
private const val LANGUAGE = "language-"
