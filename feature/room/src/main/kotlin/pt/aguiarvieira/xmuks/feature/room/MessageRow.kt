package pt.aguiarvieira.xmuks.feature.room

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyPreview
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageRow(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine = message.fromMe
    val bare = message.content.isBare()
    Column(
        modifier =
            modifier.fillMaxWidth().padding(
                start = 8.dp,
                end = 12.dp,
                top = if (message.firstInGroup) 8.dp else 2.dp
            )
    ) {
        if (!mine && message.firstInGroup) {
            Text(
                message.senderName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AVATAR_SLOT + AVATAR_GAP + 12.dp, bottom = 2.dp),
            )
        }
        // The avatar sits beside the top of the group's first bubble.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!mine) {
                Box(Modifier.width(AVATAR_SLOT)) {
                    if (message.firstInGroup) {
                        RoomAvatar(
                            message.senderName,
                            message.sender,
                            resolver.avatar(message.senderAvatarMxc),
                            size = AVATAR_SLOT
                        )
                    }
                }
                Spacer(Modifier.width(AVATAR_GAP))
            }
            Column(
                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = BUBBLE_MAX)
            ) {
                val open = {
                    media: Media,
                    kind: ViewerMedia.Kind,
                    ->
                    onOpenMedia(viewerMedia(message, media, kind, resolver))
                }
                if (bare) {
                    Content(message, resolver, MaterialTheme.colorScheme.onSurface, open)
                    Footer(message, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(horizontal = 4.dp))
                } else {
                    Bubble(message) { color ->
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.reply?.let { Reply(it, color) }
                            Content(message, resolver, color, open)
                            Footer(message, color.copy(alpha = FOOTER_ALPHA), Modifier.align(Alignment.End))
                        }
                    }
                }
                if (message.reactions.isNotEmpty()) Reactions(message.reactions, resolver, Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun viewerMedia(
    message: TimelineItem.Message,
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
) = ViewerMedia(
    kind = kind,
    url = resolver.media(media.mxc, media.encrypted).orEmpty(),
    previewUrl = timelineSource(media, kind, resolver),
    blurhash = media.blurhash,
    width = media.width,
    height = media.height,
    title = message.senderName,
    subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(message.timestamp)),
    sharedKey = SharedKeys.media(message.eventId),
)

/**
 * What the timeline loads: the sender's thumbnail when there is one (gomuks only makes avatar
 * thumbnails itself), else the original for images — Coil downsamples it to the bubble.
 */
private fun timelineSource(
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
): String? =
    media.thumbnailMxc?.let { resolver.media(it, media.thumbnailEncrypted) }
        ?: resolver.media(media.mxc, media.encrypted).takeIf { kind == ViewerMedia.Kind.Image }

/** Big emoji and stickers stand on their own, without a bubble. */
private fun MessageContent.isBare() = (this is MessageContent.Text && bigEmoji) || this is MessageContent.Sticker

@Composable
private fun Bubble(
    message: TimelineItem.Message,
    content: @Composable (Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val mine = message.fromMe
    val big = BUBBLE_RADIUS
    val small = GROUPED_RADIUS
    // Corners on the sender's side tighten where the group continues, so a run reads as one unit.
    val shape =
        if (mine) {
            RoundedCornerShape(
                big,
                if (message.firstInGroup) big else small,
                if (message.lastInGroup) big else small,
                big
            )
        } else {
            RoundedCornerShape(
                if (message.firstInGroup) big else small,
                big,
                big,
                if (message.lastInGroup) big else small
            )
        }
    val container = if (mine) colors.primaryContainer else colors.surfaceContainerHigh
    val onContainer = if (mine) colors.onPrimaryContainer else colors.onSurface
    Surface(shape = shape, color = container) { content(onContainer) }
}

@Composable
private fun Content(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    color: Color,
    onOpen: (Media, ViewerMedia.Kind) -> Unit,
) {
    val style = MaterialTheme.typography.bodyLarge
    when (val c = message.content) {
        is MessageContent.Text -> {
            TextContent(c, message.senderName, color, resolver)
        }

        is MessageContent.Image -> {
            MediaImage(c.media, resolver, c.caption, color, message.eventId, ViewerMedia.Kind.Image) {
                onOpen(c.media, ViewerMedia.Kind.Image)
            }
        }

        is MessageContent.Sticker -> {
            AsyncImage(
                model = resolver.media(c.media.mxc, c.media.encrypted),
                contentDescription = c.body.ifBlank { stringResource(R.string.sticker) },
                modifier = Modifier.size(STICKER_SIZE),
            )
        }

        is MessageContent.Video -> {
            MediaImage(c.media, resolver, c.caption, color, message.eventId, ViewerMedia.Kind.Video) {
                onOpen(c.media, ViewerMedia.Kind.Video)
            }
        }

        is MessageContent.Audio -> {
            FileCard(
                R.drawable.ic_audio,
                stringResource(R.string.audio),
                c.media.size,
                color,
                Modifier.clickable { onOpen(c.media, ViewerMedia.Kind.Audio) },
            )
        }

        is MessageContent.File -> {
            FileCard(R.drawable.ic_file, c.name, c.media.size, color)
        }

        is MessageContent.Location -> {
            FileCard(R.drawable.ic_location, c.body, null, color)
        }

        MessageContent.Redacted -> {
            Quiet(stringResource(R.string.redacted), color)
        }

        is MessageContent.Undecryptable -> {
            Quiet(stringResource(R.string.undecryptable), color)
        }

        is MessageContent.Unsupported -> {
            c.body?.takeIf { it.isNotBlank() }?.let { Text(it, color = color, style = style) }
                ?: Quiet(stringResource(R.string.unsupported, c.type), color)
        }
    }
}

@Composable
private fun TextContent(
    c: MessageContent.Text,
    sender: String,
    color: Color,
    resolver: MediaResolver,
) {
    val style =
        when {
            c.bigEmoji -> MaterialTheme.typography.displaySmall
            else -> MaterialTheme.typography.bodyLarge
        }
    val tint = if (c.kind == TextKind.Notice) color.copy(alpha = NOTICE_ALPHA) else color
    val emoteStyle = if (c.kind == TextKind.Emote) style.copy(fontStyle = FontStyle.Italic) else style
    val html = c.html
    val emote = c.kind == TextKind.Emote
    when {
        html != null && emote -> {
            HtmlContent("* ${escape(sender)} $html", tint, emoteStyle, mediaUrl = {
                resolver.media(it, false)
            })
        }

        html != null -> {
            HtmlContent(html, tint, emoteStyle, mediaUrl = { resolver.media(it, false) })
        }

        emote -> {
            PlainContent("* $sender ${c.body}", tint, emoteStyle)
        }

        else -> {
            PlainContent(c.body, tint, emoteStyle)
        }
    }
}

private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@Composable
private fun MediaImage(
    media: Media,
    resolver: MediaResolver,
    caption: String?,
    color: Color,
    eventId: String,
    kind: ViewerMedia.Kind,
    onClick: () -> Unit,
) {
    val ratio = media.aspectRatio() ?: DEFAULT_RATIO
    val placeholder = remember(media.blurhash) { media.blurhash?.let { Blurhash.decode(it)?.asImageBitmap() } }
    val source = timelineSource(media, kind, resolver)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier =
                Modifier
                    .sharedElement(SharedKeys.media(eventId))
                    .widthIn(max = MEDIA_MAX)
                    .heightIn(max = MEDIA_MAX_HEIGHT)
                    .aspectRatio(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            placeholder?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (source != null) {
                AsyncImage(
                    model = source,
                    contentDescription = caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (kind == ViewerMedia.Kind.Video) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = SCRIM)) {
                    Icon(
                        painterResource(R.drawable.ic_play),
                        null,
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
            }
        }
        caption?.let { PlainContent(it, color, MaterialTheme.typography.bodyLarge) }
    }
}

private fun Media.aspectRatio(): Float? {
    val w = width?.takeIf { it > 0 } ?: return null
    val h = height?.takeIf { it > 0 } ?: return null
    return w.toFloat() / h
}

@Composable
private fun FileCard(
    icon: Int,
    name: String,
    size: Long?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(icon), null, tint = color)
        Column {
            Text(
                name,
                color = color,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            size?.let {
                Text(
                    Formatter.formatShortFileSize(context, it),
                    color = color.copy(alpha = FOOTER_ALPHA),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun Quiet(
    text: String,
    color: Color,
) = Text(
    text,
    color = color.copy(alpha = NOTICE_ALPHA),
    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
)

@Composable
private fun Reply(
    reply: ReplyPreview,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = REPLY_BG_ALPHA)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            val sender = reply.senderName
            if (sender == null) {
                Quiet(stringResource(R.string.reply_unavailable), color)
            } else {
                Text(sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    reply.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Footer(
    message: TimelineItem.Message,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val time =
        remember(message.timestamp) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        message.sendError?.let {
            Text(
                stringResource(R.string.send_failed, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (message.edited) {
            Text(
                stringResource(R.string.edited),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(time, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Reactions(
    reactions: List<Reaction>,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { r ->
            Surface(
                shape = CircleShape,
                color = if (r.mine) colors.secondaryContainer else colors.surfaceContainerHigh,
                border = if (r.mine) androidx.compose.foundation.BorderStroke(1.dp, colors.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (r.isImage) {
                        AsyncImage(
                            model = resolver.media(r.key, false),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(r.key.take(MAX_REACTION_CHARS), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        r.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val AVATAR_SLOT = 32.dp
private val AVATAR_GAP = 6.dp
private val BUBBLE_MAX = 320.dp
private val BUBBLE_RADIUS = 20.dp
private val GROUPED_RADIUS = 6.dp
private val STICKER_SIZE = 140.dp
private val MEDIA_MAX = 260.dp
private val MEDIA_MAX_HEIGHT = 320.dp
private const val DEFAULT_RATIO = 4f / 3f
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 3f
private const val FOOTER_ALPHA = 0.7f
private const val NOTICE_ALPHA = 0.75f
private const val REPLY_BG_ALPHA = 0.08f
private const val SCRIM = 0.45f
private const val MAX_REACTION_CHARS = 24
