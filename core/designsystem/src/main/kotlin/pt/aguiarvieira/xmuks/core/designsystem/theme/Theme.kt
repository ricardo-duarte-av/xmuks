package pt.aguiarvieira.xmuks.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

/** Brand seed, used whenever the wallpaper-derived (dynamic) scheme is off. */
val XmuksSeed = Color(0xFF006A60)

/**
 * Root theme. [MaterialExpressiveTheme] gives every screen the expressive motion scheme (spatial
 * springs with overshoot), the expanded shape scale and the emphasized type styles.
 *
 * Dynamic color follows the wallpaper (minSdk 31, so always available). With it off — and in
 * screenshot tests, which must not depend on the host's wallpaper — the scheme is generated from
 * [XmuksSeed] with TonalSpot — the style Android itself uses for wallpaper colour — so the
 * fallback looks like a dynamic scheme rather than a louder, hue-rotated one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmuksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme =
        if (dynamicColor) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            rememberDynamicColorScheme(
                seedColor = XmuksSeed,
                isDark = darkTheme,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = XmuksTypography,
        content = content,
    )
}
