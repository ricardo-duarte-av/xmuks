package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyPreview
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/** Above a thread's message in the main timeline: which thread (its root), opening it. */
@Composable
internal fun ThreadLine(
    root: ReplyPreview,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).tapOrHold(onClick = onOpen).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_thread),
            null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(
                R.string.in_thread,
                root.text?.takeIf { it.isNotBlank() } ?: stringResource(R.string.thread)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Under a thread's root: how many messages it has, opening it. */
@Composable
internal fun RepliesChip(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.clip(RoundedCornerShape(50)).tapOrHold(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_thread), null, Modifier.size(16.dp))
            Text(
                pluralStringResource(R.plurals.thread_replies, count, count),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * A thread's message in the main timeline, compact (gomuks' "compact thread messages"): one line
 * with its sender and text, opening the thread; a long press still opens its menu.
 */
@Composable
internal fun CompactThreadRow(
    message: TimelineItem.Message,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .tapOrHold(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_thread),
            null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            message.senderName,
            style = MaterialTheme.typography.labelLarge,
            color = senderColor(message.label.profileId ?: message.sender),
            maxLines = 1,
        )
        Text(
            summaryOf(message.content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * In the main timeline, a thread's message shown compact (one line) when that's the setting;
 * true if it was, and nothing more is to be drawn for it.
 */
@Composable
internal fun compactInThread(
    message: TimelineItem.Message,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
): Boolean {
    val openThread = actions.openThread ?: return false
    val thread = message.thread ?: return false
    if (!actions.compactThreads) return false
    CompositionLocalProvider(LocalMessageHold provides { actions.onMessageMenu(message) }) {
        CompactThreadRow(message, { openThread(thread.eventId) }, modifier)
    }
    return true
}

/** Around a message in the main timeline: the thread it's in, above; the replies it has, below. */
@Composable
internal fun ThreadDecorations(
    message: TimelineItem.Message,
    actions: TimelineActions,
    content: @Composable () -> Unit,
) {
    val openThread = actions.openThread
    val thread = message.thread
    if (thread != null && openThread != null) ThreadLine(thread, { openThread(thread.eventId) })
    content()
    if (message.threadReplies > 0 && openThread != null) {
        RepliesChip(message.threadReplies, { openThread(message.eventId) }, Modifier.padding(top = 4.dp))
    }
}

/** One line for any message: its text, or what kind of thing it is. */
@Composable
internal fun summaryOf(content: MessageContent): String =
    when (content) {
        is MessageContent.Text -> content.body
        is MessageContent.Image -> content.caption ?: stringResource(R.string.summary_image)
        is MessageContent.Video -> content.caption ?: stringResource(R.string.summary_video)
        is MessageContent.Audio -> stringResource(R.string.summary_audio)
        is MessageContent.File -> content.name
        is MessageContent.Sticker -> content.body
        is MessageContent.Location -> content.body
        MessageContent.Redacted -> stringResource(R.string.redacted)
        else -> ""
    }
