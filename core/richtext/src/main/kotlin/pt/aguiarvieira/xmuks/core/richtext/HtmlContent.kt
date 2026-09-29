package pt.aguiarvieira.xmuks.core.richtext

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import coil3.compose.AsyncImage

/** Renders gomuks' sanitised HTML. [mediaUrl] turns `mxc://` into a loadable URL. */
@Composable
fun HtmlContent(
    html: String,
    color: Color,
    style: TextStyle,
    mediaUrl: (String) -> String?,
    modifier: Modifier = Modifier,
    lastLine: LastLine? = null,
    prefix: AnnotatedString? = null,
    preserveWhitespace: Boolean = false,
    onOpenImage: ((mxc: String, alt: String) -> Unit)? = null,
) {
    val images = InlineImages(mediaUrl, onOpenImage)
    val colors = htmlColors()
    val options = LocalRichTextOptions.current
    val blocks =
        remember(html, colors, prefix, preserveWhitespace, options.inlineImages) {
            withPrefix(HtmlParser(colors, preserveWhitespace, options.inlineImages).parse(html), prefix)
        }
    // Only a closing paragraph has a last line a footer can share; quotes, lists and code don't.
    if (blocks.lastOrNull() !is HtmlBlock.Paragraph) lastLine?.clear()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            val last = if (index == blocks.lastIndex && block is HtmlBlock.Paragraph) lastLine else null
            Block(block, color, style, images, last)
        }
    }
}

/** Plain text with web links made tappable. */
@Composable
fun PlainContent(
    body: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    lastLine: LastLine? = null,
    prefix: AnnotatedString? = null,
) {
    val link = MaterialTheme.colorScheme.primary
    val text = remember(body, link, prefix) { prefix?.plus(linkify(body, link)) ?: linkify(body, link) }
    Text(text, color = color, style = style, modifier = modifier, onTextLayout = { lastLine?.update(it) })
}

/** How inline images (custom emoji) load, and what tapping one does. */
private class InlineImages(
    val url: (String) -> String?,
    val open: ((mxc: String, alt: String) -> Unit)?,
)

/** [prefix] runs into the first paragraph (an emote's "* Name"), or stands as its own line. */
private fun withPrefix(
    blocks: List<HtmlBlock>,
    prefix: AnnotatedString?,
): List<HtmlBlock> {
    if (prefix == null) return blocks
    val first = blocks.firstOrNull()
    return if (first is HtmlBlock.Paragraph) {
        listOf(HtmlBlock.Paragraph(prefix + first.text)) + blocks.drop(1)
    } else {
        listOf(HtmlBlock.Paragraph(prefix)) + blocks
    }
}

@Composable
private fun Block(
    block: HtmlBlock,
    color: Color,
    style: TextStyle,
    images: InlineImages,
    lastLine: LastLine? = null,
) {
    val colors = MaterialTheme.colorScheme
    when (block) {
        is HtmlBlock.Paragraph -> {
            RichText(block.text, color, style, images, lastLine)
        }

        is HtmlBlock.Heading -> {
            RichText(
                block.text,
                color,
                style.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize =
                        style.fontSize * headingScale(block.level)
                ),
                images
            )
        }

        is HtmlBlock.Quote -> {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(colors.outline, RoundedCornerShape(2.dp)))
                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.blocks.forEach { Block(it, color.copy(alpha = QUOTE_ALPHA), style, images) }
                }
            }
        }

        is HtmlBlock.Code -> {
            val wrap = LocalRichTextOptions.current.wrapCode
            Text(
                block.code,
                style = style.copy(fontFamily = FontFamily.Monospace),
                color = color,
                softWrap = wrap,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                        .padding(8.dp),
            )
        }

        is HtmlBlock.Bullets -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (block.ordered) "${block.start + index}." else "•", color = color, style = style)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { item.forEach { Block(it, color, style, images) } }
                    }
                }
            }
        }

        HtmlBlock.Rule -> {
            HorizontalDivider()
        }

        is HtmlBlock.Picture -> {
            Picture(block, images)
        }
    }
}

/** A sized image: its own size, narrower when the space is; opens its link, or the viewer. */
@Composable
private fun Picture(
    picture: HtmlBlock.Picture,
    images: InlineImages,
) {
    val uriHandler = LocalUriHandler.current
    val open = images.open
    val onClick =
        when {
            picture.link != null -> ({ uriHandler.openUri(picture.link) })
            open != null -> ({ open(picture.mxc, picture.alt) })
            else -> null
        }
    AsyncImage(
        model = images.url(picture.mxc),
        contentDescription = picture.alt,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .widthIn(max = picture.width.dp)
                .aspectRatio(picture.width.toFloat() / picture.height)
                .clip(RoundedCornerShape(4.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** Annotated text with inline images (custom emoji) sized to the line. */
@Composable
private fun RichText(
    text: AnnotatedString,
    color: Color,
    style: TextStyle,
    images: InlineImages,
    lastLine: LastLine? = null,
) {
    val ids =
        remember(text) {
            text
                .getStringAnnotations(0, text.length)
                .map { it.item }
                .filter { it.startsWith(HtmlParser.IMAGE_PREFIX) || it.startsWith(HtmlParser.PICTURE_PREFIX) }
                .distinct()
        }
    val density = LocalDensity.current
    // Pictures never run wider than most of the window (a bubble or a card is a little narrower).
    val maxWidth =
        with(density) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        } * MAX_PICTURE_FRACTION
    val inline =
        ids.associateWith { id ->
            val picture = parsePicture(id)
            val mxc = picture?.third ?: id.removePrefix(HtmlParser.IMAGE_PREFIX)
            val placeholder =
                if (picture == null) {
                    Placeholder(EMOJI_EM.em, EMOJI_EM.em, PlaceholderVerticalAlign.TextCenter)
                } else {
                    val scale = minOf(1f, maxWidth / picture.first.dp)
                    with(density) {
                        Placeholder(
                            (picture.first.dp * scale).toSp(),
                            (picture.second.dp * scale).toSp(),
                            PlaceholderVerticalAlign.TextBottom,
                        )
                    }
                }
            InlineTextContent(placeholder) { alt ->
                val open = images.open
                AsyncImage(
                    model = images.url(mxc),
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
                    modifier = (if (open != null) Modifier.clickable { open(mxc, alt) } else Modifier).fillMaxSize(),
                )
            }
        }
    Text(text, color = color, style = style, inlineContent = inline, onTextLayout = { lastLine?.update(it) })
}

@Composable
private fun htmlColors(): HtmlColors {
    val c = MaterialTheme.colorScheme
    return remember(c) {
        HtmlColors(
            c.primary,
            c.onSecondaryContainer,
            c.secondaryContainer,
            c.surfaceContainerHighest,
            c.onSurfaceVariant
        )
    }
}

private fun headingScale(level: Int) = if (level <= 2) HEADING_LARGE else HEADING_SMALL

private val URL = Regex("""https?://[^\s<>"']+[^\s<>"'.,;:!?)\]]""")

private fun linkify(
    body: String,
    linkColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        URL.findAll(body).forEach { match ->
            append(body, last, match.range.first)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                withLink(LinkAnnotation.Url(match.value)) { append(match.value) }
            }
            last = match.range.last + 1
        }
        append(body, last, body.length)
    }

/**
 * Opens links without ever crashing: Matrix links (`matrix:` URIs, matrix.to) go to [onOpenMatrixLink]
 * to open in the app; schemes no app handles are ignored — unlike the default handler, which throws.
 */
class SafeUriHandler(
    private val context: Context,
    private val onOpenMatrixLink: (uri: String) -> Unit = {},
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("matrix:") || uri.startsWith(MATRIX_TO)) {
            onOpenMatrixLink(uri)
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Nothing can open it; stay put.
        }
    }
}

private const val MATRIX_TO = "https://matrix.to/#/"

/** `pic:<w>x<h>:<mxc>` → (width, height, mxc); null for anything else. */
private fun parsePicture(id: String): Triple<Int, Int, String>? {
    if (!id.startsWith(HtmlParser.PICTURE_PREFIX)) return null
    val size = id.removePrefix(HtmlParser.PICTURE_PREFIX).substringBefore(':')
    val mxc = id.removePrefix(HtmlParser.PICTURE_PREFIX).substringAfter(':')
    val w = size.substringBefore('x').toIntOrNull() ?: return null
    val h = size.substringAfter('x').toIntOrNull() ?: return null
    return Triple(w, h, mxc)
}

private const val EMOJI_EM = 1.3f
private const val MAX_PICTURE_FRACTION = 0.7f
private const val QUOTE_ALPHA = 0.8f
private const val HEADING_LARGE = 1.3f
private const val HEADING_SMALL = 1.1f
