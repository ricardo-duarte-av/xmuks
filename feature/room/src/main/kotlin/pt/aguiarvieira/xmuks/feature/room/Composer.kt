package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/**
 * The room's third card: where messages are written. Grows to a few lines, then scrolls; rides
 * up with the keyboard. Sending hands the text (markdown) to the outbox and clears the field.
 */
@Composable
internal fun ComposerCard(
    state: TextFieldState,
    mode: ComposeMode,
    onSend: () -> Unit,
    onCancelMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ScreenCard(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap),
    ) {
        Column {
            ModeBanner(mode, onCancelMode)
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
                BasicTextField(
                    state = state,
                    textStyle = style,
                    cursorBrush = SolidColor(colors.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_LINES),
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    decorator = { field ->
                        Box {
                            if (state.text.isEmpty()) {
                                Text(
                                    stringResource(R.string.composer_hint),
                                    style = style,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            field()
                        }
                    },
                )
                FilledIconButton(onClick = onSend, enabled = state.text.isNotBlank()) {
                    Icon(
                        painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.send),
                    )
                }
            }
        }
    }
}

/** What the next send is: a new message, a reply, or an edit of one of ours. */
sealed interface ComposeMode {
    data object New : ComposeMode

    data class Reply(
        val message: TimelineItem.Message,
    ) : ComposeMode

    data class Edit(
        val message: TimelineItem.Message,
    ) : ComposeMode
}

/** "Replying to Ann: …" / "Editing message", with a way out. */
@Composable
private fun ModeBanner(
    mode: ComposeMode,
    onCancel: () -> Unit,
) {
    val message =
        when (mode) {
            ComposeMode.New -> return
            is ComposeMode.Reply -> mode.message
            is ComposeMode.Edit -> mode.message
        }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (mode is ComposeMode.Edit) R.drawable.ic_edit else R.drawable.ic_reply),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                if (mode is ComposeMode.Edit) {
                    stringResource(R.string.editing)
                } else {
                    stringResource(R.string.replying_to, message.senderName)
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (message.content as? MessageContent.Text)?.body?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel_reply))
        }
    }
}

/**
 * A message that didn't make it: resend or discard. For one that *may* have been sent, say so —
 * sending again could post it twice, and that's the user's call, never ours.
 */
@Composable
internal fun UnsentDialog(
    message: TimelineItem.Message,
    onResend: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unknown = message.sendState == SendState.Unknown
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (unknown) R.string.unsent_unknown_title else R.string.unsent_failed_title)) },
        text = {
            Text(if (unknown) stringResource(R.string.unsent_unknown_text) else message.sendError.orEmpty())
        },
        confirmButton = { TextButton(onClick = onResend) { Text(stringResource(R.string.resend)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
    )
}

/** Deleting is for everyone and can't be undone: confirm first. */
@Composable
internal fun DeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_LINES = 6
