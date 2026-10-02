package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** A paragraph's spoilers as shown: [text] to draw, and [covers] to draw over it once laid out. */
internal class Spoilers(
    val text: AnnotatedString,
    val covers: Modifier,
) {
    /** The text's layout, written by its `onTextLayout`; the covers follow its lines. */
    var layout by mutableStateOf<TextLayoutResult?>(null)
}

/**
 * [text]'s spoilers, hidden until tapped and hidden again by another tap; each cover fades out as
 * it's revealed and back in as it's hidden. [color] is the text's.
 */
@Composable
internal fun rememberSpoilers(
    text: AnnotatedString,
    color: Color,
): Spoilers {
    val spoilers = remember(text) { spoilerRanges(text) }
    var revealed by remember(text) { mutableStateOf(emptySet<String>()) }
    val alphas = remember(text) { spoilers.associate { it.item to Animatable(1f) } }
    LaunchedEffect(alphas, revealed) {
        alphas.forEach { (id, alpha) -> launch { alpha.animateTo(if (id in revealed) 0f else 1f, tween(REVEAL_MS)) } }
    }
    return remember(text, revealed, color) {
        if (spoilers.isEmpty()) return@remember Spoilers(text, Modifier)
        val toggle = { id: String -> revealed = if (id in revealed) revealed - id else revealed + id }
        lateinit var shown: Spoilers
        val covers =
            Modifier.drawWithContent {
                drawContent()
                val layout = shown.layout ?: return@drawWithContent
                spoilers.forEach { s ->
                    val alpha = alphas[s.item]?.value ?: 0f
                    if (alpha > 0f) drawSpoiler(layout.getPathForRange(s.start, s.end), color, alpha)
                }
            }
        shown = Spoilers(withSpoilers(text, spoilers, revealed, toggle), covers)
        shown
    }
}

/**
 * [text] as shown with its [spoilers]: each one taps to reveal and taps again to hide. A hidden
 * one's text and backgrounds are invisible (its cover is drawn by [drawSpoiler]), its links don't
 * open, and its inline images are swapped for blank ones ([HIDDEN_PREFIX]).
 */
internal fun withSpoilers(
    text: AnnotatedString,
    spoilers: List<AnnotatedString.Range<String>>,
    revealed: Set<String>,
    toggle: (String) -> Unit,
): AnnotatedString {
    val hidden = spoilers.filter { it.item !in revealed }

    fun inHidden(
        start: Int,
        end: Int,
    ) = hidden.any { it.start < end && start < it.end }
    return buildAnnotatedString {
        append(text.text)
        text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        text.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        text.getStringAnnotations(0, text.length).forEach {
            val item = if (it.tag == INLINE_TAG && inHidden(it.start, it.end)) HIDDEN_PREFIX + it.item else it.item
            addStringAnnotation(it.tag, item, it.start, it.end)
        }
        // Spoilers first: a link inside a revealed one is laid over it, so the link wins the tap.
        spoilers.forEach { s ->
            addLink(LinkAnnotation.Clickable(HtmlParser.SPOILER_TAG + s.item, PLAIN) { toggle(s.item) }, s.start, s.end)
        }
        text.getLinkAnnotations(0, text.length).forEach { link ->
            if (inHidden(link.start, link.end)) return@forEach
            when (val item = link.item) {
                is LinkAnnotation.Url -> addLink(item, link.start, link.end)
                is LinkAnnotation.Clickable -> addLink(item, link.start, link.end)
            }
        }
        hidden.forEach {
            addStyle(
                SpanStyle(color = Color.Transparent, background = Color.Transparent),
                it.start,
                it.end
            )
        }
    }
}

/**
 * A spoiler's cover over [path] (its text's lines): a rounded wash of [color] speckled with dots,
 * at [alpha] (1 hidden, fading to 0 as it's revealed).
 */
internal fun DrawScope.drawSpoiler(
    path: Path,
    color: Color,
    alpha: Float,
) {
    val paint =
        Paint().apply {
            this.color = color.copy(alpha = WASH_ALPHA * alpha)
            pathEffect = PathEffect.cornerPathEffect(CORNER.dp.toPx())
        }
    drawIntoCanvas { it.drawPath(path, paint) }
    val bounds = path.getBounds()
    val step = SPECKLE_STEP.dp.toPx()
    val bright = mutableListOf<Offset>()
    val dim = mutableListOf<Offset>()
    var y = bounds.top + step / 2
    while (y < bounds.bottom) {
        var x = bounds.left + step / 2
        while (x < bounds.right) {
            // Fixed jitter from the position: the speckles never move between frames.
            val h = (x.toInt() * HASH_X) xor (y.toInt() * HASH_Y)
            val dot = Offset(x + jitter(h, step), y + jitter(h shr HASH_SHIFT, step))
            if ((h shr PICK_SHIFT) and 1 == 0) bright += dot else dim += dot
            x += step
        }
        y += step
    }
    val size = SPECKLE.dp.toPx()
    clipPath(path) {
        drawPoints(bright, PointMode.Points, color, strokeWidth = size, cap = StrokeCap.Round, alpha = BRIGHT * alpha)
        drawPoints(dim, PointMode.Points, color, strokeWidth = size, cap = StrokeCap.Round, alpha = DIM * alpha)
    }
}

private fun jitter(
    hash: Int,
    step: Float,
): Float = ((hash and JITTER_MASK) / JITTER_MASK.toFloat() - CENTRE) * step

/** Tapping a spoiler isn't a link: no link's underline. */
private val PLAIN = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.None))

/** An inline image inside a hidden spoiler: the same space, nothing drawn. */
internal const val HIDDEN_PREFIX = "hidden:"
internal const val INLINE_TAG = "androidx.compose.foundation.text.inlineContent"

private const val REVEAL_MS = 250
private const val CENTRE = 0.5f
private const val WASH_ALPHA = 0.14f
private const val BRIGHT = 0.7f
private const val DIM = 0.35f
private const val CORNER = 4
private const val SPECKLE_STEP = 3.5f
private const val SPECKLE = 1.4f
private const val HASH_X = 73_856_093
private const val HASH_Y = 19_349_663
private const val HASH_SHIFT = 8
private const val PICK_SHIFT = 16
private const val JITTER_MASK = 0xFF
