package pt.aguiarvieira.xmuks.core.designsystem.theme

import android.util.LruCache
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.animateColorScheme
import com.materialkolor.ktx.quantize
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A room in its own colours: the scheme grown from [seed] (its avatar's colour, see
 * [rememberAvatarSeed]) in the app's style, light or dark as the app is. Without a seed it keeps
 * the app's scheme. Changes fade, so a room's colours arriving a moment after it opens don't jump.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoomTheme(
    seed: Color?,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < HALF
    val seeded =
        seed?.let {
            rememberDynamicColorScheme(
                seedColor = it,
                isDark = dark,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
        }
    val scheme = animateColorScheme(seeded ?: base)
    val senderColors = remember(scheme) { SenderColors.from(scheme) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = XmuksTypography,
    ) {
        CompositionLocalProvider(LocalSenderColors provides senderColors, content = content)
    }
}

/**
 * The colour of the avatar at [url] that a scheme can grow from (see [dominantColor]), or null: no
 * avatar, one that hasn't loaded, or one with no usable colour (a black-and-white logo keeps the
 * app's scheme).
 * Loaded through the app's image loader, so an avatar the room list has shown costs nothing, and
 * remembered per avatar for as long as the app runs.
 */
@Composable
fun rememberAvatarSeed(url: String?): Color? {
    val context = LocalPlatformContext.current
    val seed by produceState(initialValue = url?.let { AvatarSeeds.cache.get(it) }?.color, url) {
        if (url == null) {
            value = null
            return@produceState
        }
        AvatarSeeds.cache.get(url)?.let {
            value = it.color
            return@produceState
        }
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(SEED_PX)
                .allowHardware(false)
                .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        // Not loaded (offline, say): nothing remembered, so the next opening tries again.
        if (bitmap == null) return@produceState
        val color =
            withContext(Dispatchers.Default) {
                dominantColor(QuantizerCelebi.quantize(bitmap.asImageBitmap(), QUANTIZE_COLORS))?.let(::Color)
            }
        AvatarSeeds.cache.put(url, AvatarSeeds.Seed(color))
        value = color
    }
    return seed
}

/**
 * The colour an image is mostly made of, from its quantized [colors] (ARGB to pixel count): the
 * largest group of similar hues, and in it the most common colour. Greys, near-white and
 * near-black don't count, and an image less than [MIN_SHARE] colourful has none. Not Material's
 * own scoring (as for wallpapers), which favours a vivid accent over what the picture is: a
 * yellow duck with a teal laptop would come out teal.
 */
internal fun dominantColor(colors: Map<Int, Int>): Int? {
    val total = colors.values.sum()
    val colourful =
        colors.filterKeys {
            val hct = Hct.fromInt(it)
            hct.chroma >= MIN_CHROMA && hct.tone in MIN_TONE..MAX_TONE
        }
    if (total == 0 || colourful.values.sum() < total * MIN_SHARE) return null
    val byHue = colourful.entries.groupBy { (Hct.fromInt(it.key).hue / HUE_SPAN).toInt() }
    val biggest = byHue.values.maxByOrNull { group -> group.sumOf { it.value } } ?: return null
    return biggest.maxBy { it.value }.key
}

/** Avatars' seed colours, kept while the app runs; a seed of null means "no usable colour". */
private object AvatarSeeds {
    class Seed(
        val color: Color?,
    )

    val cache = LruCache<String, Seed>(CACHED)
    private const val CACHED = 256
}

/** Plenty to find an image's colours in, and quick to quantize. */
private const val SEED_PX = 96
private const val QUANTIZE_COLORS = 128
private const val MIN_CHROMA = 16.0
private const val MIN_TONE = 10.0
private const val MAX_TONE = 95.0
private const val MIN_SHARE = 0.05
private const val HUE_SPAN = 30.0
private const val HALF = 0.5f
