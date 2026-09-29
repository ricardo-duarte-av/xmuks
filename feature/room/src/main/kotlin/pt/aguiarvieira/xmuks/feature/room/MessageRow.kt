package pt.aguiarvieira.xmuks.feature.room

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyPreview
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash
import pt.aguiarvieira.xmuks.core.richtext.HtmlContent
import pt.aguiarvieira.xmuks.core.richtext.LastLine
import pt.aguiarvieira.xmuks.core.richtext.PlainContent
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageRow(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    if (message.isEmote) {
        EmoteRow(message, resolver, actions, modifier, highlighted)
        return
    }
    val mine = message.fromMe
    // Others' groups open with a header (avatar + name); their bubbles then start at the left
    // margin, using the width an avatar gutter would waste on every message.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .highlight(highlighted)
                .padding(horizontal = EDGE, vertical = 0.dp)
                .padding(top = if (message.firstInGroup) GROUP_GAP else MESSAGE_GAP),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine && message.firstInGroup) Header(message, resolver, actions)
        // Ours carry no header, except to say which per-message profile they went out as.
        if (mine && message.firstInGroup && message.label.profileName != null) OwnProfileHeader(message, resolver)
        val open = {
            media: Media,
            kind: ViewerMedia.Kind,
            ->
            actions.openMedia(viewerMedia(message, media, kind, resolver))
        }
        BubbleRow(message, resolver, actions, open)
        if (message.reactions.isNotEmpty()) {
            Reactions(message.reactions, resolver, Modifier.maxWidthFraction(BUBBLE_FRACTION).padding(top = 4.dp)) {
                actions.onReaction(message, it)
            }
        }
    }
}

@Composable
private fun BubbleContent(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    color: Color,
    open: (Media, ViewerMedia.Kind) -> Unit,
    actions: TimelineActions,
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = BUBBLE_PADDING_V),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message.reply?.let { reply -> Reply(reply, color, onClick = { actions.jumpTo(reply.eventId) }) }
        // Text-like content shares its last line with the time when there's room; media (which
        // never reports a last line) keeps the time below.
        val lastLine = remember(message.content) { LastLine() }
        ContentWithFooter(lastLine, footer = { Footer(message, color.copy(alpha = FOOTER_ALPHA)) }) {
            Content(message, resolver, color, open, actions, lastLine)
        }
    }
}

/**
 * The bubble (or bare content) with its read receipts: they share its row, level with its bottom
 * edge — at the far right for others' messages, just left of the bubble for ours.
 */
@Composable
private fun BubbleRow(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    actions: TimelineActions,
    open: (Media, ViewerMedia.Kind) -> Unit,
) {
    val mine = message.fromMe
    val receipts = message.readBy
    // Ours that didn't go out: tap to resend or discard.
    val stuck =
        message.localId != null && (message.sendState == SendState.Failed || message.sendState == SendState.Unknown)
    Row(
        modifier =
            Modifier.fillMaxWidth().combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = { if (stuck) actions.onUnsent(message) },
                onLongClick = { actions.onMessageMenu(message) },
            ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (mine &&
            receipts.isNotEmpty()
        ) {
            ReadReceipts(receipts, resolver, actions.openUser, Modifier.padding(end = RECEIPT_GAP))
        }
        // Without receipts beside it a bubble still leaves the far side free.
        val cap = if (receipts.isEmpty()) Modifier.maxWidthFraction(BUBBLE_FRACTION) else Modifier
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false).then(cap),
        ) {
            if (message.content.isBare()) {
                Content(message, resolver, MaterialTheme.colorScheme.onSurface, open, actions)
                Footer(message, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(horizontal = 4.dp))
            } else {
                Bubble(message) { color -> BubbleContent(message, resolver, color, open, actions) }
            }
        }
        if (!mine &&
            receipts.isNotEmpty()
        ) {
            ReadReceipts(receipts, resolver, actions.openUser, Modifier.padding(start = RECEIPT_GAP))
        }
    }
}

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
    actions: TimelineActions,
    lastLine: LastLine? = null,
) {
    val style = MaterialTheme.typography.bodyLarge
    when (val c = message.content) {
        is MessageContent.Text -> {
            TextContent(c, color, resolver, lastLine) { mxc, alt ->
                resolver.image(mxc, alt, message.senderName)?.let(actions.openMedia)
            }
        }

        is MessageContent.Image -> {
            MediaImage(
                c.media,
                resolver,
                c.caption,
                color,
                message.eventId,
                ViewerMedia.Kind.Image,
                message.uploadProgress
            ) {
                onOpen(c.media, ViewerMedia.Kind.Image)
            }
        }

        is MessageContent.Sticker -> {
            AsyncImage(
                model = resolver.media(c.media.mxc, c.media.encrypted),
                contentDescription = c.body.ifBlank { stringResource(R.string.sticker) },
                modifier = Modifier.size(STICKER_SIZE).clickable { onOpen(c.media, ViewerMedia.Kind.Image) },
            )
        }

        is MessageContent.Video -> {
            MediaImage(
                c.media,
                resolver,
                c.caption,
                color,
                message.eventId,
                ViewerMedia.Kind.Video,
                message.uploadProgress
            ) {
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
            Box(contentAlignment = Alignment.Center) {
                FileCard(R.drawable.ic_file, c.name, c.media.size, color)
                message.uploadProgress?.let { UploadProgress(it) }
            }
        }

        is MessageContent.Location -> {
            FileCard(R.drawable.ic_location, c.body, null, color)
        }

        MessageContent.Redacted -> {
            Quiet(stringResource(R.string.redacted), color, lastLine)
        }

        is MessageContent.Undecryptable -> {
            Quiet(stringResource(R.string.undecryptable), color, lastLine)
        }

        is MessageContent.Unsupported -> {
            c.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = color, style = style, onTextLayout = { layout -> lastLine?.update(layout) })
            } ?: Quiet(stringResource(R.string.unsupported, c.type), color, lastLine)
        }
    }
}

@Composable
private fun TextContent(
    c: MessageContent.Text,
    color: Color,
    resolver: MediaResolver,
    lastLine: LastLine? = null,
    onOpenImage: (mxc: String, alt: String) -> Unit,
) {
    val style = if (c.bigEmoji) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge
    val tint = if (c.kind == TextKind.Notice) color.copy(alpha = NOTICE_ALPHA) else color
    val html = c.html
    if (html != null) {
        HtmlContent(
            html,
            tint,
            style,
            { resolver.media(it, false) },
            lastLine = lastLine,
            preserveWhitespace = c.plainText,
            onOpenImage = onOpenImage,
        )
    } else {
        PlainContent(c.body, tint, style, lastLine = lastLine)
    }
}

@Composable
private fun MediaImage(
    media: Media,
    resolver: MediaResolver,
    caption: String?,
    color: Color,
    eventId: String,
    kind: ViewerMedia.Kind,
    /** Still uploading: how far along. */
    uploadProgress: Float?,
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
                    .clickable(enabled = uploadProgress == null, onClick = onClick),
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
            if (uploadProgress != null) {
                UploadProgress(uploadProgress)
            } else if (kind == ViewerMedia.Kind.Video) {
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

/** A ring filling up as the upload goes, on a dark disc so it reads over any image. */
@Composable
private fun UploadProgress(progress: Float) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = SCRIM)) {
        CircularProgressIndicator(
            progress = { progress },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.padding(10.dp).size(32.dp),
        )
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
    lastLine: LastLine? = null,
) = Text(
    text,
    color = color.copy(alpha = NOTICE_ALPHA),
    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
    onTextLayout = { lastLine?.update(it) },
)

@Composable
internal fun Reply(
    reply: ReplyPreview,
    color: Color,
    onClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .background(color.copy(alpha = REPLY_BG_ALPHA)),
    ) {
        val sender = reply.sender
        val bar = sender?.let { senderColor(it.profileId ?: it.senderId) } ?: color.copy(alpha = NOTICE_ALPHA)
        Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (sender == null) {
                Quiet(stringResource(R.string.reply_unavailable), color)
            } else {
                SenderName(sender, MaterialTheme.typography.labelMedium)
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

/** Screen edge to bubble, both sides. */
private val EDGE = 12.dp
private val RECEIPT_GAP = 6.dp
private val GROUP_GAP = 8.dp
private val MESSAGE_GAP = 2.dp
private val BUBBLE_PADDING_V = 6.dp

/** Bubbles leave the far side free, so whose message it is stays obvious. */
private const val BUBBLE_FRACTION = 0.86f

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
