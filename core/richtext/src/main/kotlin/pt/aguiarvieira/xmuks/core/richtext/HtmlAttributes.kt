package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.ui.graphics.Color
import org.jsoup.nodes.Element
import kotlin.math.roundToInt

/**
 * An image's size in dp: gomuks writes it as CSS pixels (`style="width: …px; height: …px"`),
 * already fitted in 320×240; raw HTML has `width`/`height` attributes, fitted the same way.
 */
internal fun imageSize(el: Element): Pair<Int, Int>? {
    val style = el.attr("style")
    val w =
        CSS_WIDTH
            .find(style)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull() ?: el.attr("width").toFloatOrNull()
    val h =
        CSS_HEIGHT
            .find(style)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull() ?: el.attr("height").toFloatOrNull()
    if (w == null || h == null) return null
    if (w <= 0f || h <= 0f) return null
    val scale = minOf(1f, MAX_IMAGE_WIDTH / w, MAX_IMAGE_HEIGHT / h)
    return (w * scale).roundToInt() to (h * scale).roundToInt()
}

/**
 * gomuks rewrites inline images to its own media path (`_gomuks/media/{server}/{id}?…`); back to
 * the `mxc://` the rest of the app resolves. Anything else (a web URL, a file) is refused: only
 * Matrix media is ever loaded.
 */
internal fun toMxc(src: String): String? {
    if (src.startsWith("mxc://")) return src
    val path = src.substringBefore('?').removePrefix("/").takeIf { it.startsWith(GOMUKS_MEDIA) } ?: return null
    val (server, id) = path.removePrefix(GOMUKS_MEDIA).split('/', limit = 2).takeIf { it.size == 2 } ?: return null
    return "mxc://$server/$id"
}

internal fun parseColor(value: String): Color? {
    if (!value.matches(HEX_COLOR)) return null
    return Color(("FF" + value.removePrefix("#")).toLong(HEX_RADIX))
}

private const val MAX_IMAGE_WIDTH = 320f
private const val MAX_IMAGE_HEIGHT = 240f
private val CSS_WIDTH = Regex("""(?:^|;)\s*width:\s*([0-9.]+)px""")
private val CSS_HEIGHT = Regex("""(?:^|;)\s*height:\s*([0-9.]+)px""")
internal val CSS_COLOR = Regex("""(?:^|;)\s*color:\s*(#[0-9a-fA-F]{6})""")
internal val CSS_BACKGROUND = Regex("""background-color:\s*(#[0-9a-fA-F]{6})""")
private const val GOMUKS_MEDIA = "_gomuks/media/"
private const val HEX_RADIX = 16
private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
