package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Where a message's last text line ends, so its footer (time, "edited") can share that line.
 * Written by the text's own layout pass, read by the footer layout right after measuring it — a
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
