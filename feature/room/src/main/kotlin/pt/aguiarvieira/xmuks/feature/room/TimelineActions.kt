package pt.aguiarvieira.xmuks.feature.room

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.Reader
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor
import java.text.DateFormat
import java.util.Date

/** What a timeline row can ask the screen to do. */
@Immutable
class TimelineActions(
    val openMedia: (ViewerMedia) -> Unit,
    /** Someone's profile. */
    val openUser: (userId: String) -> Unit = {},
    /** Plays voice messages, audio and videos in their bubbles. */
    val player: InlinePlayer? = null,
    /** Show this event: scroll to it if loaded, else load a window around it. */
    val jumpTo: (eventId: String) -> Unit,
    /** One of our messages that didn't go out: offer to resend or discard it. */
    val onUnsent: (TimelineItem.Message) -> Unit = {},
    /** Long press: what can be done with this message (reply, edit, copy…). */
    val onMessageMenu: (TimelineItem.Message) -> Unit = {},
    /** Tapping a reaction under a message. */
    val onReaction: (TimelineItem.Message, String) -> Unit = { _, _ -> },
    /** Saving a message's file where the user picks. */
    val saveMedia: (Media) -> Unit = {},
    /** Opens a thread from its root; null inside a thread (nothing to open from there). */
    val openThread: ((rootId: String) -> Unit)? = null,
    /** Threads' messages in the main timeline as one line each (gomuks' `small_threads`). */
    val compactThreads: Boolean = true,
)

/** Briefly tints the row a jump landed on, so the eye finds it. */
@Composable
internal fun Modifier.highlight(on: Boolean): Modifier {
    val color by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = HIGHLIGHT_ALPHA) else Color.Transparent,
        animationSpec = tween(HIGHLIGHT_FADE_MS),
        label = "highlight",
    )
    return background(color, RoundedCornerShape(12.dp))
}

/**
 * Who has read up to here: up to three small overlapping avatars, then "+N", newest reader in
 * front. Sits beside the bubble, level with its bottom edge. Tapping lists everyone with the time
 * they read it. Receipts move live as sync_complete brings new ones.
 */
@Composable
internal fun ReadReceipts(
    readers: List<Reader>,
    resolver: MediaResolver,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shown = readers.take(MAX_SHOWN)
    val names = readers.joinToString { it.name }
    val description = pluralStringResource(R.plurals.read_by, readers.size, readers.size, names)
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .clickable { open = true }
                .padding(2.dp)
                .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RING + STEP * (shown.size - 1))) {
            // Drawn back to front, so the newest reader sits on top at the right.
            shown.asReversed().forEachIndexed { index, reader ->
                Box(
                    Modifier
                        .offset(x = STEP * index)
                        .size(RING)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(1.dp),
                ) {
                    RoomAvatar(reader.name, reader.userId, resolver.avatar(reader.avatarMxc), size = AVATAR)
                }
            }
        }
        if (readers.size > MAX_SHOWN) {
            Text(
                "+${readers.size - MAX_SHOWN}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
    if (open) {
        ReadersDialog(readers, resolver, onOpenUser = {
            open = false
            onOpenUser(it)
        }) { open = false }
    }
}

/** Everyone whose receipt is on this message: avatar, name, Matrix ID and when they read it. */
@Composable
private fun ReadersDialog(
    readers: List<Reader>,
    resolver: MediaResolver,
    onOpenUser: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    stringResource(R.string.read_by_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = DIALOG_MAX_HEIGHT).padding(top = 8.dp)) {
                    items(
                        readers,
                        key = { it.userId }
                    ) { reader -> ReaderRow(reader, resolver) { onOpenUser(reader.userId) } }
                }
            }
        }
    }
}

@Composable
private fun ReaderRow(
    reader: Reader,
    resolver: MediaResolver,
    onClick: () -> Unit,
) {
    val time = remember(reader.timestamp) { readTime(reader.timestamp) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(reader.name, reader.userId, resolver.avatar(reader.avatarMxc), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                reader.name,
                style = MaterialTheme.typography.titleSmall,
                color = senderColor(reader.userId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reader.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Today: just the time; otherwise the date and time. */
private fun readTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = Date(timestamp)
    val today = DateUtils.isToday(timestamp)
    return if (today) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }
}

private val DIALOG_MAX_HEIGHT = 420.dp
private const val MAX_SHOWN = 3
private val AVATAR = 16.dp
private val RING = 18.dp
private val STEP = 11.dp
private const val HIGHLIGHT_ALPHA = 0.55f
private const val HIGHLIGHT_FADE_MS = 600
