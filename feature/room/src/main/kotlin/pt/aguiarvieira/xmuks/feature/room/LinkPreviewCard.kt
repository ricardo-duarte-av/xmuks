package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.LinkPreview
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash

/**
 * A link's preview under its message: the site's title and description, and its picture — wide
 * ones across the top, squarer ones as a thumbnail beside the text. Tapping opens the link.
 */
@Composable
internal fun LinkPreviewCard(
    preview: LinkPreview,
    resolver: MediaResolver,
    color: Color,
    eventId: String,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    val wide = preview.image?.takeIf { (it.aspectRatio() ?: 1f) >= WIDE }
    val thumb = preview.image?.takeIf { wide == null }
    val open = { runCatching { uri.openUri(preview.url) } }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = TINT))
            .tapOrHold { open() },
    ) {
        if (wide != null) {
            PreviewImage(
                wide,
                resolver,
                eventId,
                { open() },
                Modifier.fillMaxWidth().aspectRatio(wide.aspectRatio() ?: 1f)
            )
        }
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    preview.title ?: preview.url,
                    color = color,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                preview.description?.let {
                    Text(
                        it,
                        color = color.copy(alpha = QUIET),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (thumb != null) {
                PreviewImage(thumb, resolver, eventId, { open() }, Modifier.size(THUMB).clip(RoundedCornerShape(8.dp)))
            }
        }
    }
}

/** The preview's picture, behind a tap when media previews are off (gomuks' `show_media_previews`). */
@Composable
private fun PreviewImage(
    image: Media,
    resolver: MediaResolver,
    eventId: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberReveal("$eventId:${image.mxc}", image, uploading = false)
    val placeholder = remember(image.blurhash) { image.blurhash?.let { Blurhash.decode(it)?.asImageBitmap() } }
    Box(modifier.tapOrHold(onClick = { reveal.onTap?.invoke() ?: onOpen() }), contentAlignment = Alignment.Center) {
        placeholder?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        if (reveal.revealed) {
            val source = resolver.media(image.mxc, image.encrypted)
            val loader = resolver.images
            if (loader != null) {
                AsyncImage(source, null, loader, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            } else {
                AsyncImage(source, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
        } else {
            TapToShow()
        }
    }
}

private const val WIDE = 1.2f
private const val TINT = 0.08f
private const val QUIET = 0.75f
private val THUMB = 64.dp
