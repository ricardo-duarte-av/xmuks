package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs

/** How timeline media is shown here, from gomuks' preferences. */
@Immutable
data class MediaDisplay(
    /** Images and videos load at once; if not, their blurhash waits for a tap. */
    val showPreviews: Boolean = true,
    val maxWidth: Dp = DEFAULT_WIDTH,
    /** GIFs play on their own; if not, a still (or blurhash) until tapped. */
    val autoplayGifs: Boolean = false,
) {
    val maxHeight: Dp get() = maxWidth * HEIGHT_PER_WIDTH

    companion object {
        /** gomuks' default width (320 px) is ours (260 dp): other widths scale from there. */
        private val DEFAULT_WIDTH = 260.dp
        private const val GOMUKS_DEFAULT_PX = 320f
        private const val HEIGHT_PER_WIDTH = 320f / 260f

        fun of(layers: PrefLayers) =
            MediaDisplay(
                showPreviews = layers.get(Prefs.showMediaPreviews),
                maxWidth = DEFAULT_WIDTH * (layers.get(Prefs.maxImageWidth) / GOMUKS_DEFAULT_PX),
                autoplayGifs = layers.get(Prefs.autoplayGifs),
            )
    }
}

val LocalMediaDisplay = staticCompositionLocalOf { MediaDisplay() }
