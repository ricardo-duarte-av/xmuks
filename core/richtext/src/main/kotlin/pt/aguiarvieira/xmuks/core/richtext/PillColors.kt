package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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
            val start = maxOf(pill.start, layout.getLineStart(line))
            val end = minOf(pill.end, layout.getLineEnd(line, visibleEnd = true))
            if (end <= start) continue
            val first = layout.getBoundingBox(start)
            val last = layout.getBoundingBox(end - 1)
            val left = minOf(first.left, last.left)
            val right = maxOf(first.right, last.right)
            val top = layout.getLineTop(line) + inset
            val height = layout.getLineBottom(line) - inset - top
            drawRoundRect(color, Offset(left, top), Size(right - left, height), CornerRadius(height / 2))
        }
    }
}

/** Taken off the line's top and bottom, as a fraction of the font size: pills on adjacent lines don't touch. */
private const val PILL_INSET = 0.08f
