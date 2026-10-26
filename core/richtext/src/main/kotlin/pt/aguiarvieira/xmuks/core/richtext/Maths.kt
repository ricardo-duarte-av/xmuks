package pt.aguiarvieira.xmuks.core.richtext

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.jsoup.nodes.Element
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * LaTeX drawn with JLaTeXMath: [drawable] is null when it can't be (a formula it can't parse), and
 * the message's own fallback text shows instead.
 */
internal object Maths {
    @Volatile private var ready = false

    /** [latex] drawn [textPx] high in [color]; null when it can't be drawn. */
    fun drawable(
        context: Context,
        latex: String,
        textPx: Float,
        color: Int,
    ): JLatexMathDrawable? {
        if (!ready) {
            synchronized(this) {
                if (!ready) {
                    JLatexMathAndroid.init(context.applicationContext)
                    ready = true
                }
            }
        }
        return runCatching {
            JLatexMathDrawable
                .builder(latex)
                .textSize(textPx)
                .color(color)
                .align(JLatexMathDrawable.ALIGN_LEFT)
                .build()
        }.getOrNull()?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
    }
}

/** What maths is drawn at: the text's own size. */
private fun TextStyle.mathsPx(density: Density): Float =
    with(density) { (if (fontSize.isSpecified) fontSize else 16.sp).toPx() }

/**
 * Inline maths' drawing in a placeholder [height] high, the drawing [top] down it. Short maths
 * (`x^2`) sits on the baseline like text, its depth hanging into the font's descent. [tall] maths
 * (a matrix, a fraction) is centred on the text instead, padded so its baseline still lands on the
 * line's: a centred placeholder is the only one that grows its line both up and down, and only
 * once the paragraph drops its fixed line height (which every line keeps, whatever it holds).
 */
internal class InlineMaths(
    val drawable: JLatexMathDrawable,
    val height: Int,
    val top: Int,
    val tall: Boolean,
)

@Composable
internal fun rememberInlineMaths(
    latex: String,
    style: TextStyle,
    color: Color,
    measurer: TextMeasurer,
): InlineMaths? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = style.mathsPx(density)
    // The font's own ascent and descent: a line of it without the paragraph's line height.
    val font = remember(style, measurer) { measurer.measure("x", style.copy(lineHeight = TextUnit.Unspecified)) }
    return remember(latex, px, color, font) {
        Maths.drawable(context, latex, px, color.toArgb())?.let { drawable ->
            inlineMaths(drawable, ascent = -font.firstBaseline, descent = font.size.height - font.firstBaseline)
        }
    }
}

/** [drawable] placed for a font [ascent] (negative) and [descent] from the baseline. */
private fun inlineMaths(
    drawable: JLatexMathDrawable,
    ascent: Float,
    descent: Float,
): InlineMaths {
    val depth = drawable.icon().iconDepth.coerceIn(0, drawable.intrinsicHeight)
    val above = (drawable.intrinsicHeight - depth).coerceAtLeast(1)
    if (above <= -ascent && depth <= descent) return InlineMaths(drawable, above, top = 0, tall = false)
    // A text-centred placeholder is centred on the font's middle: half of it reaches up past the
    // drawing's top, half down past its bottom.
    val middle = (ascent + descent) / 2
    val half = ceil(maxOf(above + middle, depth - middle)).toInt()
    return InlineMaths(drawable, 2 * half, top = (half - above - middle).roundToInt(), tall = true)
}

/** The placeholder for [maths]: as wide as it, on the baseline (short) or the text's middle (tall). */
internal fun InlineMaths.placeholder(density: Density): Placeholder =
    with(density) {
        val align = if (tall) PlaceholderVerticalAlign.TextCenter else PlaceholderVerticalAlign.AboveBaseline
        Placeholder(drawable.intrinsicWidth.toSp(), height.toSp(), align)
    }

/** Inline maths drawn in its placeholder; short maths' depth (a `y`'s tail) hangs under it. */
@Composable
internal fun InlineMathsDrawing(maths: InlineMaths) {
    val density = LocalDensity.current
    val width = with(density) { maths.drawable.intrinsicWidth.toDp() }
    val height = with(density) { maths.height.toDp() }
    Canvas(Modifier.size(width, height)) {
        translate(top = maths.top.toFloat()) {
            drawIntoCanvas { canvas ->
                maths.drawable.setBounds(0, 0, maths.drawable.intrinsicWidth, maths.drawable.intrinsicHeight)
                maths.drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}

/** Display maths: a little larger than the text, scrolling sideways when wider than the bubble. */
@Composable
internal fun DisplayMaths(
    block: HtmlBlock.Maths,
    color: Color,
    style: TextStyle,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = style.mathsPx(density) * DISPLAY_SCALE
    val drawable = remember(block.latex, px, color) { Maths.drawable(context, block.latex, px, color.toArgb()) }
    if (drawable == null) {
        Text(block.fallback, color = color, style = style)
        return
    }
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        val width = with(density) { drawable.intrinsicWidth.toDp() }
        val height = with(density) { drawable.intrinsicHeight.toDp() }
        Canvas(Modifier.size(width, height)) {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}

private const val DISPLAY_SCALE = 1.2f

private const val MATHS_TAG = "hicli-math"
private const val MATHS_ATTR = "data-mx-maths"

/** Display maths: gomuks' `<hicli-math displaymode="block">`, or a raw `<div data-mx-maths>`. */
internal fun displayMaths(el: Element): HtmlBlock.Maths? {
    val latex =
        when {
            el.normalName() == MATHS_TAG && el.attr("displaymode") == "block" -> el.attr("latex")
            el.normalName() == "div" && el.hasAttr(MATHS_ATTR) -> el.attr(MATHS_ATTR)
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null
    return HtmlBlock.Maths(latex, el.text().ifBlank { latex })
}

/**
 * Inline maths (gomuks' `<hicli-math>`, raw HTML's `<span data-mx-maths>`) as a placeholder for
 * the caller to draw, its content the fallback; false when [el] isn't maths.
 */
internal fun AnnotatedString.Builder.appendMaths(
    el: Element,
    ws: WhitespaceWriter,
): Boolean {
    val latex =
        when (el.normalName()) {
            MATHS_TAG -> el.attr("latex")
            "span" -> el.attr(MATHS_ATTR)
            else -> ""
        }
    if (latex.isBlank()) return false
    appendInlineContent("${HtmlParser.MATHS_PREFIX}$latex", el.text().ifBlank { latex })
    ws.wroteContent()
    return true
}
