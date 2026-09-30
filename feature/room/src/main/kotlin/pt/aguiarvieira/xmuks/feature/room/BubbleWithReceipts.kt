package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A bubble and its read receipts on one row, bottoms level: receipts just left of our bubbles, at
 * the far right of others'. The bubble's widest is fixed by the row alone — never more than
 * [fraction] of it, and always leaving [reserve] for receipts — so it keeps its shape as receipts
 * arrive, move on, or grow to "+N".
 */
@Composable
internal fun BubbleWithReceipts(
    mine: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier,
    reserve: Dp = RECEIPTS_RESERVE,
    receipts: (@Composable () -> Unit)? = null,
    bubble: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(bubble, receipts ?: {}),
        modifier = modifier,
    ) { (bubbles, receiptList), constraints ->
        val width = constraints.maxWidth
        val max = min((width * fraction).roundToInt(), width - reserve.roundToPx()).coerceAtLeast(0)
        val b = bubbles.first().measure(constraints.copy(minWidth = 0, maxWidth = max))
        val r =
            receiptList.firstOrNull()?.measure(
                constraints.copy(minWidth = 0, maxWidth = (width - b.width).coerceAtLeast(0))
            )
        val height = maxOf(b.height, r?.height ?: 0)
        layout(width, height) {
            if (mine) {
                b.place(width - b.width, height - b.height)
                r?.place(width - b.width - r.width, height - r.height)
            } else {
                b.place(0, height - b.height)
                r?.place(width - r.width, height - r.height)
            }
        }
    }
}

/** Room for three receipt avatars, a "+N" after them, and the gap to the bubble. */
private val RECEIPTS_RESERVE = 72.dp
