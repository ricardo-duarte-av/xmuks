package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val sent = message.localId == null && message.eventId.startsWith("$")
    val text = (message.content as? MessageContent.Text)?.body
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (sent) Item(R.drawable.ic_reply, R.string.reply) { onReply() }
            if (sent && message.editSource != null) Item(R.drawable.ic_edit, R.string.edit) { onEdit() }
            if (sent && message.edited) Item(R.drawable.ic_history, R.string.view_edits) { onHistory() }
            if (sent && message.content == MessageContent.Redacted) {
                Item(R.drawable.ic_history, R.string.view_deleted) { onHistory() }
            }
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
