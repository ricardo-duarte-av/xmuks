package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Block-level pieces of a formatted message. */
sealed interface HtmlBlock {
    data class Paragraph(
        val text: AnnotatedString,
    ) : HtmlBlock

    data class Heading(
        val level: Int,
        val text: AnnotatedString,
    ) : HtmlBlock

    data class Quote(
        val blocks: List<HtmlBlock>,
    ) : HtmlBlock

    data class Code(
        val code: String,
    ) : HtmlBlock

    data class Bullets(
        val ordered: Boolean,
        val start: Int,
        val items: List<List<HtmlBlock>>,
    ) : HtmlBlock

    data object Rule : HtmlBlock
}

/** Colours the parser needs from the theme. */
data class HtmlColors(
    val link: Color,
    val mention: Color,
    val mentionBackground: Color,
    val codeBackground: Color,
    val spoiler: Color,
)

/**
 * Parses gomuks' sanitised message HTML (`local_content.sanitized_html`) into blocks of annotated
 * text. Inline images (custom emoji) become inline-content placeholders keyed `img:<src>` with
 * their alternate text. Mentions (gomuks rewrites matrix.to links to `matrix:` URIs with the
 * `hicli-matrix-uri-*` classes) render as tinted pills.
 */
class HtmlParser(
    private val colors: HtmlColors,
) {
    fun parse(html: String): List<HtmlBlock> = blocks(Jsoup.parseBodyFragment(html).body().childNodes())

    private fun blocks(nodes: List<Node>): List<HtmlBlock> {
        val out = mutableListOf<HtmlBlock>()
        val inline = mutableListOf<Node>()

        fun flush() {
            if (inline.isEmpty()) return
            val text = inlineText(inline)
            if (text.text.isNotBlank() || text.hasImages()) out += HtmlBlock.Paragraph(text.trimNewlines())
            inline.clear()
        }
        for (node in nodes) {
            val block = (node as? Element)?.let(::block)
            if (block == null) {
                inline += node
            } else {
                flush()
                out += block
            }
        }
        flush()
        return out
    }

    /** The blocks an element makes, or null when it's inline and belongs to the running paragraph. */
    private fun block(el: Element): List<HtmlBlock>? =
        when (el.normalName()) {
            "p", "div" -> {
                blocks(el.childNodes())
            }

            "blockquote" -> {
                listOf(HtmlBlock.Quote(blocks(el.childNodes())))
            }

            "pre" -> {
                listOf(HtmlBlock.Code(el.wholeText().trimEnd('\n')))
            }

            "ul", "ol" -> {
                listOf(
                    HtmlBlock.Bullets(
                        ordered = el.normalName() == "ol",
                        start = el.attr("start").toIntOrNull() ?: 1,
                        items = el.children().filter { it.normalName() == "li" }.map { blocks(it.childNodes()) },
                    ),
                )
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                listOf(HtmlBlock.Heading(el.normalName()[1].digitToInt(), inlineText(el.childNodes()).trimNewlines()))
            }

            "hr" -> {
                listOf(HtmlBlock.Rule)
            }

            else -> {
                null
            }
        }

    private fun inlineText(nodes: List<Node>): AnnotatedString = buildAnnotatedString { nodes.forEach { append(it) } }

    private fun AnnotatedString.Builder.append(node: Node) {
        when (node) {
            is TextNode -> append(node.wholeText)
            is Element -> appendElement(node)
        }
    }

    private fun AnnotatedString.Builder.children(el: Element) = el.childNodes().forEach { append(it) }

    private fun AnnotatedString.Builder.appendElement(el: Element) {
        when (el.normalName()) {
            "br" -> {
                append('\n')
            }

            "b", "strong" -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children(el) }
            }

            "i", "em" -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children(el) }
            }

            "u", "ins" -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { children(el) }
            }

            "s", "del", "strike" -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { children(el) }
            }

            "code" -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground)) {
                    append(el.wholeText())
                }
            }

            "sup" -> {
                withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = SMALL.em)) { children(el) }
            }

            "sub" -> {
                withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = SMALL.em)) { children(el) }
            }

            "a" -> {
                appendLink(el)
            }

            "img" -> {
                appendImage(el)
            }

            "span", "font" -> {
                appendSpan(el)
            }

            // Block elements nested in inline context: keep their text, separated by a line break.
            "p", "div", "li", "blockquote", "pre" -> {
                children(el)
                append('\n')
            }

            else -> {
                children(el)
            }
        }
    }

    private fun AnnotatedString.Builder.appendLink(el: Element) {
        val href = el.attr("href")
        val mention = el.classNames().any { it.startsWith("hicli-matrix-uri") }
        if (mention) {
            withStyle(
                SpanStyle(
                    color = colors.mention,
                    background = colors.mentionBackground,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                withLink(LinkAnnotation.Url(href)) { children(el) }
            }
        } else if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) {
            withStyle(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)) {
                withLink(LinkAnnotation.Url(href)) { children(el) }
            }
        } else {
            children(el)
        }
    }

    /** Inline image (custom emoji); rendered by the caller through InlineTextContent. */
    private fun AnnotatedString.Builder.appendImage(el: Element) {
        val src = el.attr("src")
        val alt = el.attr("alt").ifBlank { el.attr("title") }.ifBlank { "🖼" }
        if (src.isBlank()) {
            append(alt)
            return
        }
        appendInlineContent("$IMAGE_PREFIX$src", alt)
    }

    private fun AnnotatedString.Builder.appendSpan(el: Element) {
        if (el.hasAttr("data-mx-spoiler")) {
            withStyle(SpanStyle(color = colors.spoiler, background = colors.spoiler)) { children(el) }
            return
        }
        val color = (el.attr("data-mx-color").ifBlank { el.attr("color") }).let(::parseColor)
        val background = el.attr("data-mx-bg-color").let(::parseColor)
        withStyle(
            SpanStyle(color = color ?: Color.Unspecified, background = background ?: Color.Unspecified)
        ) { children(el) }
    }

    private fun parseColor(value: String): Color? {
        if (!value.matches(HEX_COLOR)) return null
        return Color(("FF" + value.removePrefix("#")).toLong(HEX_RADIX))
    }

    private fun AnnotatedString.hasImages() = getStringAnnotations(INLINE_TAG, 0, length).isNotEmpty()

    /** Strips leading/trailing line breaks left over from block boundaries. */
    private fun AnnotatedString.trimNewlines(): AnnotatedString {
        val start = text.indexOfFirst { it != '\n' }.takeIf { it >= 0 } ?: return AnnotatedString("")
        val end = text.indexOfLast { it != '\n' } + 1
        return subSequence(start, end)
    }

    companion object {
        const val IMAGE_PREFIX = "img:"
        private const val INLINE_TAG = "androidx.compose.foundation.text.inlineContent"
        private const val SMALL = 0.75f
        private const val HEX_RADIX = 16
        private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
    }
}
