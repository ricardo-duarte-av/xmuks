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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.jsoup.nodes.Element
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

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

/** Inline maths' drawing, sized for its placeholder; null to show its fallback text instead. */
internal class InlineMaths(
    val drawable: JLatexMathDrawable,
    /** Its height above the baseline: the placeholder's, so it sits on the line like text. */
    val above: Int,
)

@Composable
internal fun rememberInlineMaths(
    latex: String,
    style: TextStyle,
    color: Color,
): InlineMaths? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = style.mathsPx(density)
    return remember(latex, px, color) {
        Maths.drawable(context, latex, px, color.toArgb())?.let { drawable ->
            val depth = drawable.icon().iconDepth.coerceIn(0, drawable.intrinsicHeight)
            InlineMaths(drawable, (drawable.intrinsicHeight - depth).coerceAtLeast(1))
        }
    }
}

/** The placeholder for [maths]: as wide as it, as tall as its part above the baseline. */
internal fun InlineMaths.placeholder(density: Density): Placeholder =
    with(density) {
        Placeholder(drawable.intrinsicWidth.toSp(), above.toSp(), PlaceholderVerticalAlign.AboveBaseline)
    }

/** Inline maths drawn in its placeholder; what's below the baseline (a fraction's lower half) hangs under it. */
@Composable
internal fun InlineMathsDrawing(maths: InlineMaths) {
    val density = LocalDensity.current
    val width = with(density) { maths.drawable.intrinsicWidth.toDp() }
    val height = with(density) { maths.above.toDp() }
    Canvas(Modifier.size(width, height)) {
        drawIntoCanvas { canvas ->
            maths.drawable.setBounds(0, 0, maths.drawable.intrinsicWidth, maths.drawable.intrinsicHeight)
            maths.drawable.draw(canvas.nativeCanvas)
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
