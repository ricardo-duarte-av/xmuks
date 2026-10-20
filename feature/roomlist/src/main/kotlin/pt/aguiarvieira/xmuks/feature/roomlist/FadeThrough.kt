package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Material's fade through, for switching between top-level destinations that aren't side by side:
 * the old content fades out, then the new one fades in while growing from 92 %.
 */
@Stable
internal class FadeThrough {
    val shown = Animatable(1f)
    var entering by mutableStateOf(false)

    /** Fades out, runs [swap] while nothing shows, then fades the new content in. */
    suspend fun run(swap: suspend () -> Unit) {
        entering = false
        shown.animateTo(0f, tween(OUT_MS, easing = FastOutLinearInEasing))
        swap()
        entering = true
        shown.animateTo(1f, tween(IN_MS, easing = EmphasizedDecelerate))
        entering = false
    }
}

@Composable
internal fun rememberFadeThrough() = remember { FadeThrough() }

internal fun Modifier.fadeThrough(fade: FadeThrough) =
    graphicsLayer {
        alpha = fade.shown.value
        if (fade.entering) {
            val scale = START_SCALE + (1f - START_SCALE) * fade.shown.value
            scaleX = scale
            scaleY = scale
        }
    }

// 300 ms in all, a third of it fading out.
private const val OUT_MS = 90
private const val IN_MS = 210
private const val START_SCALE = 0.92f
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
