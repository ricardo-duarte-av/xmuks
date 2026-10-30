package pt.aguiarvieira.xmuks.feature.room

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Action
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.media
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.richtext.JsonViewer

/** Which sheet or dialog the room screen has open (at most one of each). */
internal class OverlayState {
    var unsent by mutableStateOf<TimelineItem.Message?>(null)
    var menuFor by mutableStateOf<TimelineItem.Message?>(null)

    /** The pinned messages' list. */
    var pinsShown by mutableStateOf(false)

    /** A message was opened from the pinned list: back returns to the list. */
    var backToPins by mutableStateOf(false)
    var deleting by mutableStateOf<TimelineItem.Message?>(null)

    /** Our message whose file is being replaced: the photo picker is up for it. */
    var replacing by mutableStateOf<TimelineItem.Message?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)

    /** The + sheet is open. */
    var attaching by mutableStateOf(false)

    /** Choosing the per-message profile to send as. */
    var choosingPersona by mutableStateOf(false)

    /** Recording a voice message. */
    var recordingVoice by mutableStateOf(false)

    /** Picking a place on the map. */
    var pickingLocation by mutableStateOf(false)

    /** Writing a poll. */
    var writingPoll by mutableStateOf(false)
}

/** An open picker: for a reaction to [reactTo], or for the composer. */
internal class PickerRequest(
    val mode: PickerMode,
    val reactTo: TimelineItem.Message? = null,
    /** The composer's picker grown from the keyboard's place into a full-height sheet. */
    val expanded: Boolean = false,
)

/** The room's sheets and dialogs: message menu, emoji/sticker picker, delete, history, unsent. */
@Composable
internal fun RoomOverlays(
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
    actions: TimelineActions,
    onSaveMedia: (Media) -> Unit = {},
    onOpenThread: ((String) -> Unit)? = null,
    pins: PinsUi = PinsUi(),
    /** Forwarding a message's media; null where it isn't offered. */
    onForward: ((String) -> Unit)? = null,
    /** A message's file or text to another app; null where it isn't offered. */
    onShare: ((TimelineItem.Message) -> Unit)? = null,
) {
    state.menuFor?.let { message ->
        MessageMenuFor(message, state, composer, onSaveMedia, onOpenThread, pins, onForward, onShare)
    }
    PickerSheet(state, composer, resolver)
    state.deleting?.let { message -> DeleteFor(message, state, composer) }
    composer.history?.let { MessageHistorySheet(it, resolver, actions, composer.onHideHistory) }
    composer.reactions?.let { ReactionsSheet(it, resolver, actions.openUser, composer.onHideReactions) }
    composer.source?.let { SourceFor(it, composer.onHideSource) }
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

/** The emoji/sticker picker as a sheet: for a reaction, or the composer's grown to full height. */
@Composable
private fun PickerSheet(
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
) {
    val request = state.picker ?: return
    val target = request.reactTo
    if (target == null) {
        // The composer's pickers live in place of the keyboard (ComposerPanel); grown, in a sheet.
        if (request.expanded) ExpandedPicker(request, state, composer, resolver)
        return
    }
    EmojiPickerSheet(
        mode = request.mode,
        packs = composer.emoji.packs,
        recent = composer.emoji.recent,
        resolver = resolver,
        blocked = { picked -> reactionLimit(composer, picked) != null },
        onPick = { picked ->
            composer.emoji.onReact(target, picked)
            state.picker = null
        },
        onSubscribe = composer.emoji.onSubscribe,
        onDismiss = { state.picker = null },
    )
}

/**
 * The most used Unicode emoji (custom ones need the picker), topped up with the usual ones; where
 * the network takes only some, those first.
 */
private fun quickReactions(
    recent: List<String>,
    allowed: Set<String>?,
): List<String> {
    val all = (recent.filterNot { it.startsWith("mxc://") } + DEFAULT_QUICK_REACTIONS).distinct()
    val ordered = if (allowed == null) all else all.filter { it in allowed } + allowed + all
    return ordered.distinct().take(QUICK_REACTIONS)
}

/** Why the bridged network won't take [picked] as a reaction, if it won't. */
private fun reactionLimit(
    composer: ComposerActions,
    picked: Picked,
): Limit? = composer.features?.reactionBlock(picked.key, picked is Picked.Custom)

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
        // Stays under its full-screen sheet, so shrinking back finds it as it was.
        overlays.picker?.takeIf { it.reactTo == null }?.let { request ->
            ComposerPanel(
                request,
                composer,
                resolver,
                keyboardHeight,
                onModeChange = { overlays.picker = PickerRequest(it) },
                onExpand = { overlays.picker = PickerRequest(request.mode, expanded = true) },
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
    val launch = rememberAttachLauncher(composer.onPickMedia, composer.onPickMany)
    ReplacePicker(state, composer)
    if (state.attaching) {
        AttachSheet(
            available = composer.attachments,
            limitOf = { attachmentLimit(composer.features, it) },
            onLimit = composer.onLimit,
            onPick = {
                when (it) {
                    Attachment.Voice -> state.recordingVoice = true
                    Attachment.Location -> state.pickingLocation = true
                    Attachment.Poll -> state.writingPoll = true
                    else -> launch(it)
                }
            },
            onSendAs = if (composer.personas.choices.isEmpty()) null else ({ state.choosingPersona = true }),
            onDismiss = { state.attaching = false },
        )
    }
    composer.mediaDraft?.let { draft ->
        MediaSendScreen(
            draft,
            composer.onChooseSize,
            composer.onSendMedia,
            composer.onCancelMedia,
            composer.network,
            composer.onLimit,
            composer.captionMentions?.let { start -> CaptionMentions(start, composer.mentions.avatar) },
        )
    }
    if (state.recordingVoice) VoiceSheet(composer.onSendVoice) { state.recordingVoice = false }
    if (state.pickingLocation) LocationPicker(composer.onSendLocation) { state.pickingLocation = false }
    if (state.writingPoll) {
        val features = composer.features
        PollComposer(
            composer.polls.onStart,
            onDismiss = { state.writingPoll = false },
            maxAnswers = features?.pollMaxOptions,
            maxAnswerLength = features?.pollOptionMaxLength,
            onLimit = composer.onLimit,
        )
    }
    if (state.choosingPersona) {
        PersonaChooser(composer.personas, resolver.avatar, composer.onChoosePersona) { state.choosingPersona = false }
    }
}

/** Confirming [message]'s deletion; past the network's window, saying it stays there. */
@Composable
private fun DeleteFor(
    message: TimelineItem.Message,
    state: OverlayState,
    composer: ComposerActions,
) {
    val keptBy =
        composer.features?.deleteBlock(message.timestamp, System.currentTimeMillis())?.let {
            composer.network ?: stringResource(R.string.network_fallback)
        }
    DeleteDialog(
        matrixOnly = keptBy,
        onConfirm = {
            composer.onDelete(message)
            state.deleting = null
        },
        onDismiss = { state.deleting = null },
    )
}

/** Why the bridged network won't take [attachment], if it won't. */
private fun attachmentLimit(
    features: RoomFeatures?,
    attachment: Attachment,
): Limit? =
    when (attachment) {
        Attachment.Poll -> {
            features?.pollBlock()
        }

        Attachment.Location -> {
            features?.locationBlock()
        }

        Attachment.Voice -> {
            features
                ?.checkMedia(RoomFeatures.VOICE, RoomFeatures.VOICE_MIME, 0)
                ?.firstOrNull { it.blocks }
                ?.let(Limit::Media)
        }

        else -> {
            null
        }
    }

/** The long-press menu for [message], and what each of its items does. */
@Composable
private fun MessageMenuFor(
    message: TimelineItem.Message,
    state: OverlayState,
    composer: ComposerActions,
    onSaveMedia: (Media) -> Unit,
    onOpenThread: ((String) -> Unit)?,
    pins: PinsUi,
    onForward: ((String) -> Unit)?,
    onShare: ((TimelineItem.Message) -> Unit)?,
) {
    val features = composer.features
    val now = System.currentTimeMillis()
    val onLimit = { limit: Limit ->
        state.menuFor = null
        composer.onLimit(limit)
    }
    MessageMenu(
        message,
        limits =
            if (composer.readOnly) {
                MenuLimits.preview(onLimit)
            } else {
                MenuLimits(
                    edit = features?.editBlock(message.timestamp, now),
                    delete = features?.deleteBlock(message.timestamp, now),
                    reply = features?.replyBlock(),
                    thread = features?.threadBlock(),
                    reaction = { key -> features?.reactionBlock(key, custom = false) },
                    moreReactions = features?.takeUnless { it.reaction.works }?.let { Limit.Unsupported(Action.React) },
                    onLimit = onLimit,
                )
            },
        pinned = if (pins.pins.canPin) message.eventId in pins.pins.eventIds else null,
        onPin = {
            pins.onToggle(message.eventId)
            state.menuFor = null
        },
        onThread =
            onOpenThread?.let { open ->
                {
                    state.menuFor = null
                    open(message.thread?.eventId ?: message.eventId)
                }
            },
        onSave =
            message.content.media?.let { media ->
                {
                    onSaveMedia(media)
                    state.menuFor = null
                }
            },
        onShare =
            onShare?.takeIf { shareable(message) }?.let { share ->
                {
                    share(message)
                    state.menuFor = null
                }
            },
        onForward =
            onForward?.let { forward ->
                message.takeIf(::forwardable)?.let {
                    {
                        forward(message.eventId)
                        state.menuFor = null
                    }
                }
            },
        onEndPoll =
            (message.content as? MessageContent.Poll)?.takeIf { message.fromMe && !it.tally.ended }?.let {
                {
                    composer.polls.onEnd(message)
                    state.menuFor = null
                }
            },
        onReply = {
            composer.onReply(message)
            state.menuFor = null
        },
        onEdit = {
            composer.onEdit(message)
            state.menuFor = null
        },
        onReplace =
            message.takeIf { it.editSource != null && it.content.replaceable }?.let {
                {
                    state.replacing = message
                    state.menuFor = null
                }
            },
        onHistory = {
            composer.onShowHistory(message)
            state.menuFor = null
        },
        onReactions = {
            composer.onShowReactions(message.eventId, null)
            state.menuFor = null
        },
        onSource = {
            composer.onShowSource(message.eventId)
            state.menuFor = null
        },
        onDelete = {
            state.deleting = message
            state.menuFor = null
        },
        quickReactions = quickReactions(composer.emoji.recent, features?.allowedReactions),
        onReact = { key ->
            composer.emoji.onReact(message, Picked.Unicode(key))
            state.menuFor = null
        },
        onMoreReactions = {
            state.picker = PickerRequest(PickerMode.Emoji, reactTo = message)
            state.menuFor = null
        },
        canDeleteOthers = composer.canRedact,
        onDismiss = { state.menuFor = null },
    )
}

/** An event's JSON once it's loaded; if gomuks can't give it, a toast says so instead. */
@Composable
private fun SourceFor(
    source: SourceView,
    onHide: () -> Unit,
) {
    val json = source.json
    if (json != null) {
        JsonViewer(json, stringResource(R.string.event_source), onHide)
    } else if (source.failed) {
        val context = LocalContext.current
        LaunchedEffect(source, onHide) {
            Toast.makeText(context, R.string.event_source_failed, Toast.LENGTH_SHORT).show()
            onHide()
        }
    }
}

/**
 * The photo picker for a new file to replace [OverlayState.replacing]'s with; what's picked opens
 * on the send screen, with the message's caption and spoiler mark to keep or change. The target
 * is kept across the activity being recreated while the picker is up.
 */
@Composable
private fun ReplacePicker(
    state: OverlayState,
    composer: ComposerActions,
) {
    var eventId by rememberSaveable { mutableStateOf<String?>(null) }
    var caption by rememberSaveable { mutableStateOf("") }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    val picked: (Uri?) -> Unit = { uri ->
        val id = eventId
        if (uri != null && id != null) composer.onPickReplacement(uri, Replacing(id, caption, spoiler))
        eventId = null
    }
    // One file replaces one: the first picked.
    val launch = rememberAttachLauncher(onPick = picked, onPickMany = { picked(it.firstOrNull()) })
    val message = state.replacing
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        eventId = message.eventId
        caption = message.editSource.orEmpty()
        spoiler = message.content.media?.spoiler == true
        // Cleared now: a cancelled pick must not keep the next Replace from opening the picker.
        state.replacing = null
        launch(Attachment.Gallery)
    }
}

/** A picture or video: its file can be replaced by another (pictures and videos only). */
private val MessageContent.replaceable: Boolean
    get() = this is MessageContent.Image || this is MessageContent.Video

private const val QUICK_REACTIONS = 6
