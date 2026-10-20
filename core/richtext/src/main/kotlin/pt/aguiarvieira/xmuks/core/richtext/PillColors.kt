package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult

/** Mention pills' colours where they're drawn: what holds the text (a bubble) picks them. */
@Immutable
data class PillColors(
    val container: Color,
    val content: Color,
)

/** Unset (room list previews, profiles): secondaryContainer, as mentions always were. */
val LocalPillColors = compositionLocalOf<PillColors?> { null }

/** A text's mention pills: [modifier] draws them behind it once [layout] holds its laid-out lines. */
class Pills internal constructor(
    ranges: List<AnnotatedString.Range<String>>,
    color: Color,
) {
    /** The text's layout, written by its `onTextLayout`. */
    var layout by mutableStateOf<TextLayoutResult?>(null)

    val modifier: Modifier =
        if (ranges.isEmpty()) Modifier else Modifier.drawBehind { layout?.let { drawPills(it, ranges, color) } }
}

/** [text]'s mention pills ([HtmlParser.PILL_TAG]), in the colours of what holds it. */
@Composable
fun rememberPills(text: AnnotatedString): Pills {
    val color = htmlColors().mentionBackground
    return remember(text, color) { Pills(pillRanges(text), color) }
}

internal fun pillRanges(text: AnnotatedString): List<AnnotatedString.Range<String>> =
    text.getStringAnnotations(HtmlParser.PILL_TAG, 0, text.length)

/** Each pill in [pills] as a fully rounded shape behind its text, one per line it runs across. */
internal fun DrawScope.drawPills(
    layout: TextLayoutResult,
    pills: List<AnnotatedString.Range<String>>,
    color: Color,
) {
    val inset =
        PILL_INSET *
            layout.layoutInput.style.fontSize
                .toPx()
    pills.forEach { pill ->
        if (pill.end <= pill.start || pill.end > layout.layoutInput.text.length) return@forEach
        for (line in layout.getLineForOffset(pill.start)..layout.getLineForOffset(pill.end - 1)) {
            drawPillLine(layout, line, maxOf(pill.start, layout.getLineStart(line)), pill.end, inset, color)
        }
    }
}

/** The part of a pill from [start] to [end] that lies on [line]. */
private fun DrawScope.drawPillLine(
    layout: TextLayoutResult,
    line: Int,
    start: Int,
    end: Int,
    inset: Float,
    color: Color,
) {
    val lineEnd = minOf(end, layout.getLineEnd(line, visibleEnd = true))
    if (lineEnd <= start) return
    val first = layout.getBoundingBox(start)
    val last = layout.getBoundingBox(lineEnd - 1)
    val left = minOf(first.left, last.left)
    // An ellipsised line hides the rest of its text: the pill stops at the line's edge.
    val right = minOf(maxOf(first.right, last.right), size.width)
    if (right <= left) return
    val top = layout.getLineTop(line) + inset
    val height = layout.getLineBottom(line) - inset - top
    drawRoundRect(color, Offset(left, top), Size(right - left, height), CornerRadius(height / 2))
}

/** Taken off the line's top and bottom, as a fraction of the font size: pills on adjacent lines don't touch. */
private const val PILL_INSET = 0.08f
