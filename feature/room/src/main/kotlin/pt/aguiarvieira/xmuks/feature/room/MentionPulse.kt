package pt.aguiarvieira.xmuks.feature.room

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * A bubble that mentions us: its outline, in the theme's primary colour, breathes slowly. Only
 * the draw phase follows the pulse (no recomposition, no relayout); with system animations off
 * it's a still outline.
 */
@Composable
internal fun Modifier.mentionPulse(shape: Shape): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val still =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    if (still) return border(WIDTH, color, shape)
    val alpha =
        rememberInfiniteTransition(label = "mention").animateFloat(
            initialValue = DIM,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "mention-alpha",
        )
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(WIDTH.toPx())
        onDrawWithContent {
            drawContent()
            drawOutline(outline, color, alpha = alpha.value, style = stroke)
        }
    }
}

private val WIDTH = 2.dp
private const val DIM = 0.25f
private const val PULSE_MS = 1_200
