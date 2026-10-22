package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * What can be done with a message (long press): react, reply, edit (ours, once sent), thread,
 * pin, save, forward, copy its text, delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageMenu(
    message: TimelineItem.Message,
    /** What the bridged network won't do with it. */
    limits: MenuLimits,
    /** Its file saved where the user picks; null when it has none. */
    onSave: (() -> Unit)?,
    /** Answering in its thread (its own, or the one it's in); null inside a thread. */
    onThread: (() -> Unit)?,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Who reacted with what. */
    onReactions: () -> Unit = {},
    quickReactions: List<String> = emptyList(),
    onReact: (String) -> Unit = {},
    onMoreReactions: () -> Unit = {},
    /** Whether it's pinned; null when we may not pin or unpin. */
    pinned: Boolean? = null,
    onPin: () -> Unit = {},
    /** Ending our poll; null when it isn't one we can end. */
    onEndPoll: (() -> Unit)? = null,
    /** A new file for our picture or video; null when it isn't one. */
    onReplace: (() -> Unit)? = null,
    /** Its file sent to another room; null when it has none to forward. */
    onForward: (() -> Unit)? = null,
) {
    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val sent = message.localId == null && message.eventId.startsWith("$")
    val text = (message.content as? MessageContent.Text)?.body
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (sent) QuickReactions(quickReactions, onReact, onMoreReactions, limits)
            if (sent) SentItems(message, onReply, onEdit, onReplace, onHistory, onReactions, limits)
            if (sent) ThreadAndPin(onThread, pinned, onPin, limits)
            if (sent && onEndPoll != null) {
                Item(R.drawable.ic_poll, R.string.poll_end, limits.write, limits.onLimit, onEndPoll)
            }
            FileItems(message, sent, onSave, onForward)
            if (text != null) {
                Item(R.drawable.ic_copy, R.string.copy_text) {
                    clipboard.setText(AnnotatedString(text))
                    onDismiss()
                }
            }
            if (sent && message.fromMe && message.content != MessageContent.Redacted) {
                // Past the network's window it still goes here: said so, not dimmed.
                val label = if (limits.delete != null) R.string.delete_matrix_only else R.string.delete
                Item(R.drawable.ic_delete, label, limits.write, limits.onLimit) { onDelete() }
            }
        }
    }
}

/** Saving its file, and forwarding it once sent, as each is possible. */
@Composable
private fun FileItems(
    message: TimelineItem.Message,
    sent: Boolean,
    onSave: (() -> Unit)?,
    onForward: (() -> Unit)?,
) {
    if (onSave != null && message.uploadProgress == null) Item(R.drawable.ic_download, R.string.save, onSave)
    if (sent && onForward != null) Item(R.drawable.ic_forward, R.string.forward, onForward)
}

/** Answering in its thread, and pinning or unpinning it, as each is possible. */
@Composable
private fun ThreadAndPin(
    onThread: (() -> Unit)?,
    pinned: Boolean?,
    onPin: () -> Unit,
    limits: MenuLimits,
) {
    if (onThread != null) {
        Item(R.drawable.ic_thread, R.string.reply_in_thread, limits.thread, limits.onLimit, onThread)
    }
    if (pinned != null) {
        Item(
            if (pinned) R.drawable.ic_pin_off else R.drawable.ic_pin,
            if (pinned) R.string.unpin else R.string.pin,
            limits.write,
            limits.onLimit,
            onPin,
        )
    }
}

/**
 * What only a sent message offers: reply, edit and replace its file (ours), its edits, what a
 * deletion removed, who reacted.
 */
@Composable
private fun SentItems(
    message: TimelineItem.Message,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onReplace: (() -> Unit)?,
    onHistory: () -> Unit,
    onReactions: () -> Unit,
    limits: MenuLimits,
) {
    Item(R.drawable.ic_reply, R.string.reply, limits.reply, limits.onLimit, onReply)
    if (message.editSource != null) Item(R.drawable.ic_edit, R.string.edit, limits.edit, limits.onLimit, onEdit)
    if (onReplace != null) Item(R.drawable.ic_replace, R.string.replace_file, limits.edit, limits.onLimit, onReplace)
    if (message.edited) Item(R.drawable.ic_history, R.string.view_edits) { onHistory() }
    if (message.content == MessageContent.Redacted) Item(R.drawable.ic_history, R.string.view_deleted) { onHistory() }
    if (message.reactions.isNotEmpty()) Item(R.drawable.ic_add_reaction, R.string.view_reactions, onReactions)
}

@Composable
private fun Item(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) = Item(icon, label, null, {}, onClick)

/** An item the network may not allow ([limit]): then it's dimmed, and tapping it says why. */
@Composable
private fun Item(
    icon: Int,
    label: Int,
    limit: Limit?,
    onLimit: (Limit) -> Unit,
    onClick: () -> Unit,
) = ListItem(
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable { if (limit != null) onLimit(limit) else onClick() }.limited(limit),
) { Text(stringResource(label)) }

/** The most used emoji, one tap to react, and the full picker behind "+". */
@Composable
private fun QuickReactions(
    emoji: List<String>,
    onReact: (String) -> Unit,
    onMore: () -> Unit,
    limits: MenuLimits,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emoji.forEach { e ->
            val limit = limits.reaction(e)
            Box(
                Modifier
                    .size(QUICK)
                    .clip(CircleShape)
                    .clickable { if (limit != null) limits.onLimit(limit) else onReact(e) }
                    .limited(limit),
                contentAlignment = Alignment.Center,
            ) {
                Text(e, fontSize = 24.sp)
            }
        }
        val more = limits.moreReactions
        FilledTonalIconButton(
            onClick = { if (more != null) limits.onLimit(more) else onMore() },
            modifier = Modifier.size(QUICK).limited(more),
        ) {
            Icon(
                painterResource(R.drawable.ic_add_reaction),
                contentDescription = stringResource(R.string.add_reaction)
            )
        }
    }
}

/** Shown until recent emoji exist: the usual first reactions. */
internal val DEFAULT_QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
private val QUICK = 44.dp
