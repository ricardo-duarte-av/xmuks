package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.media
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash
import pt.aguiarvieira.xmuks.core.richtext.HtmlContent
import pt.aguiarvieira.xmuks.core.richtext.LastLine
import pt.aguiarvieira.xmuks.core.richtext.PlainContent

/** One gallery item's ID for shared elements and saved state: unique within the timeline. */
internal fun galleryKey(
    eventId: String,
    index: Int,
) = "$eventId#$index"

/**
 * An MSC4274 gallery: its items as a grid (an odd one out spans the first row), then its caption.
 * Past [MAX_TILES] the last tile says how many more; tapping it shows them all.
 */
@Composable
internal fun GalleryContent(
    message: TimelineItem.Message,
    gallery: MessageContent.Gallery,
    resolver: MediaResolver,
    color: Color,
    actions: TimelineActions,
    lastLine: LastLine? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GalleryGrid(message, gallery.items, resolver, actions)
        gallery.caption?.let { caption ->
            val style = MaterialTheme.typography.bodyLarge
            val html = gallery.html
            if (html != null) {
                HtmlContent(html, color, style, { resolver.media(it, false) }, lastLine = lastLine)
            } else {
                PlainContent(caption, color, style, lastLine = lastLine)
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    message: TimelineItem.Message,
    items: List<MessageContent>,
    resolver: MediaResolver,
    actions: TimelineActions,
) {
    val display = LocalMediaDisplay.current
    var expanded by rememberSaveable(message.eventId) { mutableStateOf(false) }
    // Without previews, one tap shows the whole gallery (spoilers still wait for their own tap).
    var tapped by rememberSaveable(message.eventId) { mutableStateOf(false) }
    val waiting = !display.showPreviews && !tapped
    val shown = if (expanded || items.size <= MAX_TILES) items.size else MAX_TILES
    val hidden = items.size - shown
    Box(contentAlignment = Alignment.Center) {
        Column(Modifier.width(display.maxWidth), verticalArrangement = Arrangement.spacedBy(GAP)) {
            rowsOf(shown).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    row.forEach { index ->
                        GalleryTile(
                            message = message,
                            index = index,
                            item = items[index],
                            resolver = resolver,
                            actions = actions,
                            more = if (index == shown - 1) hidden else 0,
                            waiting = waiting,
                            onReveal = { tapped = true },
                            onMore = { expanded = true },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(tileRatio(items[index], wide = row.size == 1, alone = shown == 1)),
                        )
                    }
                }
            }
        }
        if (waiting && items.take(shown).any(::waitsForGalleryTap)) {
            Box(Modifier.tapOrHold { tapped = true }) { TapToShow() }
        }
    }
}

/** The tiles' indices by row: the first row holds one when the count is odd, two otherwise. */
private fun rowsOf(count: Int): List<List<Int>> =
    buildList {
        var i = 0
        if (count % 2 == 1) add(listOf(i++))
        while (i < count) add(listOf(i++, i++))
    }

/** A picture or video that the gallery's one "tap to show" reveals (spoilers have their own). */
private fun waitsForGalleryTap(item: MessageContent) =
    (item is MessageContent.Image || item is MessageContent.Video) && item.media?.spoiler == false

/** Pairs are square; a lone item keeps its shape; the odd one out on top of others is wide. */
private fun tileRatio(
    item: MessageContent,
    wide: Boolean,
    alone: Boolean,
): Float =
    when {
        !wide -> 1f
        alone -> item.media?.aspectRatio()?.coerceIn(MIN_RATIO, MAX_RATIO) ?: 1f
        else -> WIDE_RATIO
    }

/** A square of the grid: a picture (a video's with a play badge), or an icon and name. */
@Composable
private fun GalleryTile(
    message: TimelineItem.Message,
    index: Int,
    item: MessageContent,
    resolver: MediaResolver,
    actions: TimelineActions,
    /** Items past this one that aren't shown: drawn over it, and a tap shows them. */
    more: Int,
    /** The gallery waits for a tap to show its pictures; [onReveal] is that tap. */
    waiting: Boolean,
    onReveal: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = item.media ?: return
    val key = galleryKey(message.eventId, index)
    val kind =
        when (item) {
            is MessageContent.Image -> ViewerMedia.Kind.Image
            is MessageContent.Video -> ViewerMedia.Kind.Video
            else -> null
        }
    // Once the gallery is shown, its pictures count as shown (as our own uploads do); spoilers don't.
    val own = rememberReveal(key, media, uploading = !waiting && !media.spoiler)
    val reveal =
        if (media.spoiler ||
            !waiting
        ) {
            own
        } else {
            MediaReveal(revealed = false, animate = false, onTap = onReveal)
        }
    val open: () -> Unit = {
        when {
            more > 0 -> onMore()
            item is MessageContent.File -> actions.saveMedia(media)
            else -> actions.openMedia(viewerMedia(message, media, kind ?: ViewerMedia.Kind.Audio, resolver, key))
        }
    }
    Box(
        modifier
            .sharedElement(SharedKeys.media(key))
            .clip(RoundedCornerShape(TILE_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .tapOrHold(onClick = (if (more == 0) reveal.onTap else null) ?: open),
        contentAlignment = Alignment.Center,
    ) {
        if (kind != null) {
            VisualTile(media, kind, resolver, reveal)
        } else {
            FileTile(item, media)
        }
        if (more > 0) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM)),
                contentAlignment = Alignment.Center
            ) {
                Text("+$more", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun VisualTile(
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
    reveal: MediaReveal,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val placeholder = remember(media.blurhash) { media.blurhash?.let { Blurhash.decode(it)?.asImageBitmap() } }
        placeholder?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        val source = if (reveal.revealed) timelineSource(media, kind, resolver, reveal.animate) else null
        if (source != null) {
            val loader = resolver.images
            if (loader != null) {
                AsyncImage(source, media.name, loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                AsyncImage(source, media.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        when {
            !reveal.revealed && media.spoiler -> {
                SpoilerBadge(media.spoilerReason)
            }

            !reveal.revealed -> {}

            // the gallery's one "tap to show" says it
            media.animated && !reveal.animate -> {
                GifBadge()
            }

            kind == ViewerMedia.Kind.Video -> {
                PlayBadge()
            }
        }
    }
}

@Composable
private fun FileTile(
    item: MessageContent,
    media: Media,
) {
    val icon = if (item is MessageContent.Audio) R.drawable.ic_audio else R.drawable.ic_file
    Column(
        Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            media.name.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MAX_TILES = 6
private val GAP = 3.dp
private val TILE_RADIUS = 8.dp
private const val WIDE_RATIO = 2f
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 3f
private const val SCRIM = 0.45f
