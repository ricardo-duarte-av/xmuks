package pt.aguiarvieira.xmuks.feature.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** Which sheet or dialog the room screen has open (at most one of each). */
internal class OverlayState {
    var unsent by mutableStateOf<TimelineItem.Message?>(null)
    var menuFor by mutableStateOf<TimelineItem.Message?>(null)
    var deleting by mutableStateOf<TimelineItem.Message?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)

    /** The + sheet is open. */
    var attaching by mutableStateOf(false)

    /** Choosing the per-message profile to send as. */
    var choosingPersona by mutableStateOf(false)

    /** Recording a voice message. */
    var recordingVoice by mutableStateOf(false)

    /** Picking a place on the map. */
    var pickingLocation by mutableStateOf(false)
}

/** An open picker: for a reaction to [reactTo], or for the composer. */
internal class PickerRequest(
    val mode: PickerMode,
    val reactTo: TimelineItem.Message? = null,
)

/** The room's sheets and dialogs: message menu, emoji/sticker picker, delete, history, unsent. */
@Composable
internal fun RoomOverlays(
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
) {
    state.menuFor?.let { message ->
        MessageMenu(
            message,
            onReply = {
                composer.onReply(message)
                state.menuFor = null
            },
            onEdit = {
                composer.onEdit(message)
                state.menuFor = null
            },
            onHistory = {
                composer.onShowHistory(message)
                state.menuFor = null
            },
            onDelete = {
                state.deleting = message
                state.menuFor = null
            },
            quickReactions = quickReactions(composer.emoji.recent),
            onReact = { key ->
                composer.emoji.onReact(message, Picked.Unicode(key))
                state.menuFor = null
            },
            onMoreReactions = {
                state.picker = PickerRequest(PickerMode.Emoji, reactTo = message)
                state.menuFor = null
            },
            onDismiss = { state.menuFor = null },
        )
    }
    // The composer's pickers live in place of the keyboard (ComposerPanel); reactions get a sheet.
    state.picker?.takeIf { it.reactTo != null }?.let { request ->
        EmojiPickerSheet(
            mode = request.mode,
            packs = composer.emoji.packs,
            recent = composer.emoji.recent,
            resolver = resolver,
            onPick = { picked ->
                val target = request.reactTo
                when {
                    target != null -> {
                        composer.emoji.onReact(target, picked)
                    }

                    request.mode == PickerMode.Sticker -> {
                        (picked as? Picked.Custom)?.let { composer.emoji.onSticker(it.image) }
                    }

                    else -> {
                        composer.draft.insertAtCursor(picked.asText())
                        composer.emoji.onUsed(picked)
                    }
                }
                // Inserting into the composer keeps the state.picker open for more; the rest close it.
                if (target != null || request.mode == PickerMode.Sticker) state.picker = null
            },
            onSubscribe = composer.emoji.onSubscribe,
            onDismiss = { state.picker = null },
        )
    }
    state.deleting?.let { message ->
        DeleteDialog(
            onConfirm = {
                composer.onDelete(message)
                state.deleting = null
            },
            onDismiss = { state.deleting = null },
        )
    }
    composer.history?.let { MessageHistorySheet(it, resolver, composer.onHideHistory) }
    ComposerOverlays(state, composer, resolver)
    state.unsent?.let { message ->
        val id = message.localId ?: return@let
        UnsentDialog(
            message,
            onResend = {
                composer.onResend(id)
                state.unsent = null
            },
            onDiscard = {
                composer.onDiscard(id)
                state.unsent = null
            },
            onDismiss = { state.unsent = null },
        )
    }
}

/** The most used Unicode emoji (custom ones need the picker), topped up with the usual ones. */
private fun quickReactions(recent: List<String>): List<String> =
    (recent.filterNot { it.startsWith("mxc://") } + DEFAULT_QUICK_REACTIONS).distinct().take(QUICK_REACTIONS)

/** Emoji into the draft: Unicode as is, custom ones as `:shortcode:` (expanded when sent). */
private fun Picked.asText(): String =
    when (this) {
        is Picked.Unicode -> emoji
        is Picked.Custom -> ":${image.shortcode}:"
    }

private fun TextFieldState.insertAtCursor(text: String) {
    edit { replace(selection.min, selection.max, text) }
}

private const val QUICK_REACTIONS = 6

/**
 * The emoji/sticker picker in place of the keyboard, under the message box, so the box and the
 * timeline move up exactly as they do for the keyboard. It takes the keyboard's last height (or a
 * shorter one while its own search field has the keyboard up).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerPanel(
    request: PickerRequest,
    composer: ComposerActions,
    resolver: MediaResolver,
    keyboardHeight: Dp,
    onModeChange: (PickerMode) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val searching = WindowInsets.isImeVisible
    ScreenCard(
        Modifier
            .fillMaxWidth()
            .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
            // The card's bottom margin counts against the keyboard's height, so the box doesn't move.
            .height(if (searching) SEARCHING_HEIGHT else (keyboardHeight - ScreenCards.Gap).coerceAtLeast(MIN_PANEL)),
    ) {
        var searching by remember(request.mode) { mutableStateOf(false) }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryTabRow(
                    selectedTabIndex = request.mode.ordinal,
                    containerColor = Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    PickerMode.entries.forEach { mode ->
                        Tab(
                            selected = request.mode == mode,
                            onClick = { onModeChange(mode) },
                            text = {
                                Text(
                                    stringResource(
                                        if (mode ==
                                            PickerMode.Emoji
                                        ) {
                                            R.string.emoji
                                        } else {
                                            R.string.sticker_button
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
                IconButton(onClick = { searching = !searching }) {
                    Icon(
                        painterResource(if (searching) R.drawable.ic_close else R.drawable.ic_search),
                        stringResource(
                            if (request.mode ==
                                PickerMode.Sticker
                            ) {
                                R.string.search_stickers
                            } else {
                                R.string.search_emoji
                            }
                        ),
                    )
                }
            }
            PickerContent(request, composer, resolver, searching, onClose)
        }
    }
}

@Composable
private fun PickerContent(
    request: PickerRequest,
    composer: ComposerActions,
    resolver: MediaResolver,
    searching: Boolean,
    onClose: () -> Unit,
) {
    // Keyed by mode: switching tabs starts a fresh search and scroll.
    key(request.mode) {
        EmojiPicker(
            mode = request.mode,
            packs = composer.emoji.packs,
            recent = composer.emoji.recent,
            resolver = resolver,
            onPick = { picked ->
                if (request.mode == PickerMode.Sticker) {
                    (picked as? Picked.Custom)?.let { composer.emoji.onSticker(it.image) }
                    onClose()
                } else {
                    // Stays open for more.
                    composer.draft.insertAtCursor(picked.asText())
                    composer.emoji.onUsed(picked)
                }
            },
            onSubscribe = composer.emoji.onSubscribe,
            modifier = Modifier.padding(top = 8.dp),
            showSearch = searching,
        )
    }
}

private val MIN_PANEL = 280.dp
private val SEARCHING_HEIGHT = 200.dp

/**
 * The message box, with the composer's emoji/sticker panel under it when open: together they ride
 * the navigation bar and the keyboard, so opening either lifts the timeline the same way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerArea(
    overlays: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
    box: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Where the keyboard is heading, not where it is mid-animation: sampling it while it slides
    // away would remember a sliver.
    val ime = WindowInsets.imeAnimationTarget.getBottom(density)
    val nav = WindowInsets.navigationBars.getBottom(density)
    // The keyboard's height, remembered from the last time it was up: the panel matches it.
    var keyboardHeight by remember { mutableStateOf(0.dp) }
    if (ime > nav) keyboardHeight = with(density) { (ime - nav).toDp() }
    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        box()
        overlays.picker?.takeIf { it.reactTo == null }?.let { request ->
            ComposerPanel(
                request,
                composer,
                resolver,
                keyboardHeight,
                onModeChange = { overlays.picker = PickerRequest(it) },
            ) { overlays.picker = null }
        }
    }
}

/** Opens a composer picker in place of the keyboard (which goes away; so does the field's focus). */
internal fun openPanel(
    overlays: OverlayState,
    mode: PickerMode,
    keyboard: SoftwareKeyboardController?,
    focus: FocusManager,
) {
    keyboard?.hide()
    focus.clearFocus()
    overlays.picker = PickerRequest(mode)
}

/** The composer's own: the + sheet, an attachment's preview, and choosing who to send as. */
@Composable
private fun ComposerOverlays(
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
) {
    val launch = rememberAttachLauncher(composer.onPickMedia)
    if (state.attaching) {
        AttachSheet(
            available = composer.attachments,
            onPick = {
                when (it) {
                    Attachment.Voice -> state.recordingVoice = true
                    Attachment.Location -> state.pickingLocation = true
                    else -> launch(it)
                }
            },
            onSendAs = if (composer.personas.choices.isEmpty()) null else ({ state.choosingPersona = true }),
            onDismiss = { state.attaching = false },
        )
    }
    composer.mediaDraft?.let { draft ->
        MediaSendScreen(draft, composer.onChooseSize, composer.onSendMedia, composer.onCancelMedia)
    }
    if (state.recordingVoice) VoiceSheet(composer.onSendVoice) { state.recordingVoice = false }
    if (state.pickingLocation) LocationPicker(composer.onSendLocation) { state.pickingLocation = false }
    if (state.choosingPersona) {
        PersonaChooser(composer.personas, resolver.avatar, composer.onChoosePersona) { state.choosingPersona = false }
    }
}
