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
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * What can be done with a message (long press): reply, edit (ours, once sent), copy its text.
 * Reactions, forwarding, redaction and the rest join as M5 goes on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageMenu(
    message: TimelineItem.Message,
    /** Its file saved where the user picks; null when it has none. */
    onSave: (() -> Unit)?,
    /** Answering in its thread (its own, or the one it's in); null inside a thread. */
    onThread: (() -> Unit)?,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    quickReactions: List<String> = emptyList(),
    onReact: (String) -> Unit = {},
    onMoreReactions: () -> Unit = {},
) {
    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val sent = message.localId == null && message.eventId.startsWith("$")
    val text = (message.content as? MessageContent.Text)?.body
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (sent) QuickReactions(quickReactions, onReact, onMoreReactions)
            if (sent) SentItems(message, onReply, onEdit, onHistory)
            if (sent && onThread != null) Item(R.drawable.ic_thread, R.string.reply_in_thread, onThread)
            if (onSave != null && message.uploadProgress == null) Item(R.drawable.ic_download, R.string.save, onSave)
            if (text != null) {
                Item(R.drawable.ic_copy, R.string.copy_text) {
                    clipboard.setText(AnnotatedString(text))
                    onDismiss()
                }
            }
            if (sent && message.fromMe && message.content != MessageContent.Redacted) {
                Item(R.drawable.ic_delete, R.string.delete) { onDelete() }
            }
        }
    }
}

/** What only a sent message offers: reply, edit (ours), its edits, what a deletion removed. */
@Composable
private fun SentItems(
    message: TimelineItem.Message,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
) {
    Item(R.drawable.ic_reply, R.string.reply) { onReply() }
    if (message.editSource != null) Item(R.drawable.ic_edit, R.string.edit) { onEdit() }
    if (message.edited) Item(R.drawable.ic_history, R.string.view_edits) { onHistory() }
    if (message.content == MessageContent.Redacted) Item(R.drawable.ic_history, R.string.view_deleted) { onHistory() }
}

@Composable
private fun Item(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) = ListItem(
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(onClick = onClick),
) { Text(stringResource(label)) }

/** The most used emoji, one tap to react, and the full picker behind "+". */
@Composable
private fun QuickReactions(
    emoji: List<String>,
    onReact: (String) -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emoji.forEach { e ->
            Box(Modifier.size(QUICK).clip(CircleShape).clickable { onReact(e) }, contentAlignment = Alignment.Center) {
                Text(e, fontSize = 24.sp)
            }
        }
        FilledTonalIconButton(onClick = onMore, modifier = Modifier.size(QUICK)) {
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
