package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * A formatted message in a few lines (a reply's quote): its blocks run together as text, keeping
 * inline styling and code's monospace, never the markdown that made them. Images show their alt.
 * Links and mentions look as they do in the message but aren't tappable: a tap on a snippet is
 * for what holds it (the room list row opens the room, a quote jumps to the original).
 */
@Composable
fun HtmlSnippet(
    html: String,
    color: Color,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val text = rememberHtmlSnippet(html)
    val pills = rememberPills(text)
    Text(
        text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { pills.layout = it },
        modifier = modifier.then(pills.modifier),
    )
}

/**
 * [HtmlSnippet]'s text, for callers that put it in a line of their own (a room list preview); its
 * mentions keep their [HtmlParser.PILL_TAG] annotations, for [rememberPills] to draw.
 */
@Composable
fun rememberHtmlSnippet(html: String): AnnotatedString {
    val colors = htmlColors()
    return remember(html, colors) { snippetOf(HtmlParser(colors, images = false).parse(html), colors) }
}

internal fun snippetOf(
    blocks: List<HtmlBlock>,
    colors: HtmlColors,
): AnnotatedString =
    buildAnnotatedString {
        blocks.flatMap(::lines).forEachIndexed { i, line ->
            if (i > 0) append('\n')
            when (line) {
                is Line.Text -> {
                    val start = length
                    append(line.text.withoutLinks())
                    // Too small to tap: a quoted spoiler stays a solid block.
                    spoilerRanges(line.text).forEach {
                        addStyle(
                            SpanStyle(color = colors.spoiler, background = colors.spoiler),
                            start + it.start,
                            start + it.end
                        )
                    }
                }

                is Line.Code -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground)) {
                        append(line.code)
                    }
                }
            }
        }
    }

/** The same text and styling (spoilers' annotations included) without its link annotations. */
private fun AnnotatedString.withoutLinks(): AnnotatedString =
    buildAnnotatedString {
        append(AnnotatedString(text, spanStyles, paragraphStyles))
        getStringAnnotations(0, text.length).forEach { addStringAnnotation(it.tag, it.item, it.start, it.end) }
    }

private sealed interface Line {
    data class Text(
        val text: AnnotatedString,
    ) : Line

    data class Code(
        val code: String,
    ) : Line
}

private fun lines(block: HtmlBlock): List<Line> =
    when (block) {
        is HtmlBlock.Paragraph -> {
            listOf(Line.Text(block.text))
        }

        is HtmlBlock.Heading -> {
            listOf(Line.Text(block.text))
        }

        is HtmlBlock.Quote -> {
            block.blocks.flatMap(::lines)
        }

        is HtmlBlock.Code -> {
            listOf(Line.Code(block.code))
        }

        is HtmlBlock.Bullets -> {
            block.items.flatMap { item ->
                item.flatMap(::lines).mapIndexed { i, l ->
                    if (i ==
                        0
                    ) {
                        bullet(l)
                    } else {
                        l
                    }
                }
            }
        }

        is HtmlBlock.Picture -> {
            listOf(Line.Text(AnnotatedString(block.alt)))
        }

        HtmlBlock.Rule -> {
            emptyList()
        }
    }

private fun bullet(line: Line): Line = if (line is Line.Text) Line.Text(AnnotatedString("• ") + line.text) else line
