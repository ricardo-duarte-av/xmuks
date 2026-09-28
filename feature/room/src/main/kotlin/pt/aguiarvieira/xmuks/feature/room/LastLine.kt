package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where a message's last text line ends, so its footer (time, "edited") can share that line.
 * Written by the text's own layout pass, read by [ContentWithFooter] right after measuring it — a
 * plain holder, not state: the value is only ever needed within the same measure pass.
 */
class LastLine {
    /** Right edge of the last line, px from the content's left; -1 when it doesn't end in text. */
    var end: Int = -1
        private set

    /** From the content's bottom up to the last line's baseline, px. */
    var baselineFromBottom: Int = 0
        private set

    fun update(layout: TextLayoutResult) {
        val line = layout.lineCount - 1
        end = ceil(layout.getLineRight(line)).toInt()
        baselineFromBottom = layout.size.height - layout.getLineBaseline(line).roundToInt()
    }

    fun clear() {
        end = -1
    }
}

/**
 * [content] with [footer] tucked into the end of its last line when it fits there (WhatsApp-style);
 * otherwise the footer takes a line of its own, right-aligned. Either way nothing is wider than it
 * needs to be: the footer only adds width to a single short line.
 */
@Composable
internal fun ContentWithFooter(
    lastLine: LastLine,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(content, footer),
        modifier = modifier
    ) { (contentMeasurables, footerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val body = contentMeasurables.first().measure(loose)
        val foot = footerMeasurables.first().measure(loose)
        val gap = FOOTER_GAP.roundToPx()
        val end = lastLine.end
        if (end >= 0 && end + gap + foot.width <= loose.maxWidth) {
            val width = max(body.width, end + gap + foot.width)
            val y = body.height - lastLine.baselineFromBottom - foot[FirstBaseline]
            layout(width, max(body.height, y + foot.height)) {
                body.place(0, 0)
                foot.place(width - foot.width, y)
            }
        } else {
            val width = max(body.width, foot.width)
            layout(width, body.height + foot.height) {
                body.place(0, 0)
                foot.place(width - foot.width, body.height)
            }
        }
    }
}

/** Caps the width at [fraction] of what the parent offers (bubbles never span the whole row). */
internal fun Modifier.maxWidthFraction(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val max = (constraints.maxWidth * fraction).roundToInt()
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = max))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

private val FOOTER_GAP = 8.dp
