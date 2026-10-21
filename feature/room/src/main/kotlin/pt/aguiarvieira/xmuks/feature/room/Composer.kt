package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.richtext.HtmlSnippet

/**
 * The room's third card: where messages are written. Grows to a few lines, then scrolls; rides
 * up with the keyboard. Sending hands the text (markdown) to the outbox and clears the field.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposerCard(
    state: TextFieldState,
    mode: ComposeMode,
    onSend: () -> Unit,
    onCancelMode: () -> Unit,
    modifier: Modifier = Modifier,
    commands: List<BotCommand> = emptyList(),
    onEmoji: () -> Unit = {},
    onAttach: () -> Unit = {},
    /** The message box got focus (a tap into it): the keyboard is coming back. */
    onFocus: () -> Unit = {},
    personas: Personas = Personas(),
    avatarUrl: (String?) -> String? = { null },
    onChoosePersona: (String?) -> Unit = {},
    onOpenPersonas: () -> Unit = {},
    previews: LinkPreviewsUi = LinkPreviewsUi(),
    mentions: MentionHintsUi = MentionHintsUi(),
    /** The network a bridged room is on: the hint says the message goes there. */
    network: String? = null,
    /** GIFs and stickers from the keyboard, images pasted in: sent as they are. */
    onSendMedia: (android.net.Uri) -> Unit = {},
    /** The longest message the bridged network takes: a count shows near it, and sending past it is dimmed. */
    maxLength: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val mediaReceiver = rememberMediaReceiver(onSendMedia)
    val cards = ScreenCards.cards
    ComposerSurface(cards, modifier) {
        Column {
            ModeBanner(mode, onCancelMode)
            SendingAsBanner(personas, state, avatarUrl, onChoosePersona, onOpenPersonas)
            CommandHints(state, commands)
            MentionHints(mentions)
            ComposerLinkPreviews(previews)
            Row(
                modifier =
                    if (cards) {
                        Modifier.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    } else {
                        Modifier
                            .heightIn(min = ScreenCards.BarHeight)
                            .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                    },
                verticalAlignment = Alignment.Bottom,
            ) {
                // Drawn close together: each keeps its full touch target, overlapping a little.
                Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_OVERLAP)) {
                    IconButton(onClick = onAttach) {
                        Icon(
                            painterResource(R.drawable.ic_attach),
                            contentDescription = stringResource(R.string.attach)
                        )
                    }
                    IconButton(onClick = onEmoji) {
                        Icon(painterResource(R.drawable.ic_mood), contentDescription = stringResource(R.string.emoji))
                    }
                }
                val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
                BasicTextField(
                    state = state,
                    textStyle = style,
                    cursorBrush = SolidColor(colors.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_LINES),
                    // A message, not a code field: capitals after full stops, the keyboard's corrections.
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = true,
                            keyboardType = KeyboardType.Text,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(
                                if (cards) {
                                    // Bare in its card.
                                    Modifier.padding(vertical = 12.dp)
                                } else {
                                    // The field in a pill, as the room list's search on its bar; as tall
                                    // as the buttons beside it, so the bar keeps the tab bar's height.
                                    Modifier
                                        .heightIn(min = FIELD_HEIGHT)
                                        .background(ScreenCards.pill, RoundedCornerShape(FIELD_RADIUS))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                },
                            ).contentReceiver(mediaReceiver)
                            .onFocusChanged { if (it.isFocused) onFocus() },
                    decorator = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.text.isEmpty()) {
                                Text(
                                    when {
                                        mode.editsCaption -> stringResource(R.string.caption_hint)
                                        network != null -> stringResource(R.string.composer_hint_network, network)
                                        else -> stringResource(R.string.composer_hint)
                                    },
                                    style = style,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            field()
                        }
                    },
                )
                val length = state.text.length
                val over = maxLength != null && length > maxLength
                if (maxLength != null && length * COUNT_FROM_TENTHS >= maxLength * COUNT_AT_TENTHS) {
                    Text(
                        "$length / $maxLength",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (over) colors.error else colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                    )
                }
                // Too long for the network: still tappable, so sending can say why.
                FilledIconButton(
                    onClick = onSend,
                    enabled = state.text.isNotBlank() || mode.editsCaption,
                    modifier = Modifier.alpha(if (over) DISABLED_ALPHA else 1f),
                ) {
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
                if (mode.editsCaption) {
                    stringResource(R.string.editing_caption)
                } else if (mode is ComposeMode.Edit) {
                    stringResource(R.string.editing)
                } else {
                    stringResource(R.string.replying_to, message.senderName)
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mode.editsCaption) {
                Text(
                    summaryOf(message.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            (message.content as? MessageContent.Text)?.let { text ->
                val html = text.html
                if (html != null && !text.plainText) {
                    HtmlSnippet(html, colors.onSurfaceVariant, MaterialTheme.typography.bodySmall, maxLines = 1)
                } else {
                    Text(
                        text.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

/**
 * Deleting is for everyone and can't be undone: confirm first. [matrixOnly] names the bridged
 * network that keeps it (past its window for deleting), when one does.
 */
@Composable
internal fun DeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    matrixOnly: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Text(
                matrixOnly?.let { stringResource(R.string.delete_text_matrix_only, it) }
                    ?: stringResource(R.string.delete_text),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Flat: a bar on the ground along the bottom, as the room list's tab bar: its height, its tint.
 * Cards: a card on the ground, [ScreenCards.Gap] from the sides and the bottom.
 */
@Composable
private fun ComposerSurface(
    cards: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = if (cards) {
    ScreenCard(modifier.padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap), content)
} else {
    Surface(modifier, color = ScreenCards.ground, content = content)
}

private const val MAX_LINES = 6
private val FIELD_RADIUS = 24.dp
private val FIELD_HEIGHT = 48.dp

/** The count shows from 90% of the network's limit. */
private const val COUNT_FROM_TENTHS = 10
private const val COUNT_AT_TENTHS = 9

private val BUTTON_OVERLAP = (-12).dp
