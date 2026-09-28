package pt.aguiarvieira.xmuks.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct

/**
 * Name colours, keyed by user ID the way gomuks does it — so a person has the same colour here as
 * in gomuks web, whatever their display name. gomuks' ten fixed colours are the sources; each is
 * harmonised toward the theme's primary (M3's treatment for fixed-meaning colours) and set to the
 * tone the scheme uses for accents: 40 on light, 80 on dark.
 */
@Immutable
class SenderColors internal constructor(
    private val colors: List<Color>,
) {
    fun of(id: String): Color = colors[index(id)]

    companion object {
        /** gomuks web's `--sender-color-0..9` (light), `web/src/index.css`. */
        private val SOURCES =
            longArrayOf(
                0xFFA4041D,
                0xFF9B2200,
                0xFF803F00,
                0xFF005F00,
                0xFF005C45,
                0xFF00548C,
                0xFF064AB1,
                0xFF5D26CD,
                0xFF822198,
                0xFF9F0850,
            )

        private const val LIGHT_TONE = 40.0
        private const val DARK_TONE = 80.0
        private const val DARK_SURFACE = 0.5f

        /** gomuks' `getUserColorIndex`: the sum of the ID's UTF-16 code units, modulo the count. */
        fun index(id: String): Int = id.sumOf { it.code } % SOURCES.size

        fun from(scheme: ColorScheme): SenderColors {
            val dark = scheme.surface.luminance() < DARK_SURFACE
            val primary = scheme.primary.toArgb()
            return SenderColors(
                SOURCES.map { source ->
                    val harmonized = Blend.harmonize(source.toInt(), primary)
                    Color(Hct.fromInt(harmonized).withTone(if (dark) DARK_TONE else LIGHT_TONE).toInt())
                },
            )
        }
    }
}

val LocalSenderColors = staticCompositionLocalOf<SenderColors?> { null }

/** This ID's name colour in the current theme (provided by [XmuksTheme]; derived here otherwise). */
@Composable
fun senderColor(id: String): Color {
    val provided = LocalSenderColors.current
    if (provided != null) return provided.of(id)
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { SenderColors.from(scheme) }.of(id)
}
