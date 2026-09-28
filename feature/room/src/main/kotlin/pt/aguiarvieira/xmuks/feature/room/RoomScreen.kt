package pt.aguiarvieira.xmuks.feature.room

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.emoji.ImagePack
import pt.aguiarvieira.xmuks.core.data.emoji.PackImage
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.HeaderTitle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RoomRoute(
    roomId: String,
    sharedScope: String,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomViewModel = hiltViewModel<RoomViewModel, RoomViewModel.Factory>(key = roomId) { it.create(roomId) },
) {
    val room by viewModel.room.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val typing by viewModel.typing.collectAsStateWithLifecycle()
    val loadingOlder by viewModel.loadingOlder.collectAsStateWithLifecycle()
    val hasMoreBefore by viewModel.hasMoreBefore.collectAsStateWithLifecycle()
    val loadedEvents by viewModel.loadedEvents.collectAsStateWithLifecycle()
    val context by viewModel.context.collectAsStateWithLifecycle()
    val mode by viewModel.modes.current.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val packs by viewModel.emoji.packs.collectAsStateWithLifecycle()
    val recent by viewModel.emoji.recent.collectAsStateWithLifecycle()
    val resolver = remember(viewModel) { MediaResolver(viewModel.media::avatar, viewModel.media::media) }
    val androidContext = LocalContext.current
    val uriHandler = remember(androidContext) { SafeUriHandler(androidContext) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        RoomScreen(
            roomId = roomId,
            sharedScope = sharedScope,
            room = room,
            timeline = TimelineState(items, loadingOlder, hasMoreBefore, loadedEvents),
            context = context,
            typing = typing,
            resolver = resolver,
            onBack = onBack,
            onLoadOlder = viewModel::loadOlder,
            onOpenMedia = onOpenMedia,
            onShowContext = viewModel::showContext,
            onLeaveContext = viewModel::leaveContext,
            composer =
                ComposerActions(
                    draft = viewModel.draft,
                    onSend = viewModel::send,
                    onResend = viewModel::resend,
                    onDiscard = viewModel::discard,
                    mode = mode,
                    onReply = viewModel.modes::reply,
                    onEdit = viewModel.modes::edit,
                    onCancelMode = viewModel.modes::cancel,
                    onMarkRead = viewModel::markRead,
                    history = history,
                    onShowHistory = viewModel::showHistory,
                    onHideHistory = viewModel::hideHistory,
                    onDelete = viewModel::delete,
                    commands = commands,
                    emoji =
                        EmojiState(
                            packs = packs,
                            recent = recent,
                            onReact = viewModel.emoji::react,
                            onToggle = viewModel.emoji::toggle,
                            onSticker = viewModel.emoji::sendSticker,
                            onUsed = viewModel.emoji::used,
                            onSubscribe = viewModel.emoji::setSubscribed,
                        ),
                ),
            modifier = modifier,
        )
    }
}

/** The composer's text and what sending, resending and discarding do. */
class ComposerActions(
    val draft: TextFieldState,
    val onSend: () -> Unit,
    val onResend: (localId: String) -> Unit,
    val onDiscard: (localId: String) -> Unit,
    val mode: ComposeMode = ComposeMode.New,
    val onReply: (TimelineItem.Message) -> Unit = {},
    val onEdit: (TimelineItem.Message) -> Unit = {},
    val onCancelMode: () -> Unit = {},
    /** The newest message is on screen: the room can be marked read up to it. */
    val onMarkRead: (eventId: String) -> Unit = {},
    val history: HistoryView? = null,
    val onShowHistory: (TimelineItem.Message) -> Unit = {},
    val onHideHistory: () -> Unit = {},
    val onDelete: (TimelineItem.Message) -> Unit = {},
    val commands: List<BotCommand> = emptyList(),
    val emoji: EmojiState = EmojiState(),
)

/** What the emoji/sticker pickers show, and what picking does. */
class EmojiState(
    val packs: List<ImagePack> = emptyList(),
    val recent: List<String> = emptyList(),
    val onReact: (TimelineItem.Message, Picked) -> Unit = { _, _ -> },
    val onToggle: (TimelineItem.Message, String) -> Unit = { _, _ -> },
    val onSticker: (PackImage) -> Unit = {},
    val onUsed: (Picked) -> Unit = {},
    val onSubscribe: (ImagePack, Boolean) -> Unit = { _, _ -> },
)

/** The live timeline as the screen needs it; [items] newest first, null until the first page. */
data class TimelineState(
    val items: List<TimelineItem>?,
    val loadingOlder: Boolean = false,
    val hasMoreBefore: Boolean = true,
    /** Raw events loaded, shown or not: moves with every page. */
    val loadedEvents: Int = 0,
)

/**
 * The room as three cards on a tinted ground: the header, the timeline and (from M5) the composer.
 * Jumping to an event scrolls to it when it's loaded, or opens a window around it
 * (`get_event_context`) with a way back to the live timeline.
 */
@Composable
fun RoomScreen(
    roomId: String,
    sharedScope: String,
    room: RoomSummary?,
    timeline: TimelineState,
    context: ContextView?,
    typing: List<String>,
    resolver: MediaResolver,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onShowContext: (String) -> Unit,
    onLeaveContext: () -> Unit,
    composer: ComposerActions,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val overlays = remember { OverlayState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // After we send, the timeline follows to our message wherever it was scrolled.
    val followNext = remember { FollowRequest() }
    val liveList = rememberLazyListState()
    val contextList = remember(context?.eventId) { LazyListState() }
    var highlighted by remember { mutableStateOf<String?>(null) }
    val shown by rememberUpdatedState(if (context != null) context.items else timeline.items)
    val list by rememberUpdatedState(if (context != null) contextList else liveList)
    val showContext by rememberUpdatedState(onShowContext)
    val actions =
        remember(onOpenMedia) {
            TimelineActions(
                openMedia = onOpenMedia,
                onUnsent = { overlays.unsent = it },
                onMessageMenu = { overlays.menuFor = it },
                onReaction = composer.emoji.onToggle,
                jumpTo = { eventId ->
                    val index = shown?.indexOfEvent(eventId) ?: -1
                    if (index >= 0) {
                        highlighted = eventId
                        scope.launch { list.animateScrollToItem(index, list.focusOffset()) }
                    } else {
                        showContext(eventId)
                    }
                },
            )
        }
    JumpEffects(context, contextList, onLeaveContext) { highlighted = it }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            delay(HIGHLIGHT_MS)
            highlighted = null
        }
    }
    BackHandler(enabled = context != null, onBack = onLeaveContext)
    RoomOverlays(overlays, composer, resolver)

    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        // Each card handles its own insets: the header the status bar, the composer the
        // navigation bar and the keyboard.
        contentWindowInsets = WindowInsets(0),
        topBar = { HeaderCard(roomId, sharedScope, room, typing, resolver, onBack, onOpenMedia) },
        bottomBar = {
            ComposerArea(overlays, composer, resolver) {
                ComposerCard(
                    composer.draft,
                    composer.mode,
                    {
                        followNext.requested = true
                        composer.onSend()
                    },
                    composer.onCancelMode,
                    commands = composer.commands,
                    onEmoji = { openPanel(overlays, PickerMode.Emoji, keyboard, focus) },
                    onSticker = { openPanel(overlays, PickerMode.Sticker, keyboard, focus) },
                    onFocus = { if (overlays.picker?.reactTo == null) overlays.picker = null },
                )
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(
                    padding
                ).padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .fillMaxSize()
        ) {
            if (context != null) {
                ContextTimeline(context, contextList, resolver, actions, highlighted, onLeaveContext)
            } else {
                LiveTimeline(timeline, liveList, followNext, resolver, actions, highlighted, onLoadOlder)
                MarkReadAtBottom(timeline.items, liveList, composer.onMarkRead)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderCard(
    roomId: String,
    sharedScope: String,
    room: RoomSummary?,
    typing: List<String>,
    resolver: MediaResolver,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
) {
    ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                HeaderTitle(
                    id = roomId,
                    name = room?.name.orEmpty(),
                    avatarUrl = room?.avatarUrl,
                    sharedScope = sharedScope,
                    subtitle = typingText(typing),
                    onAvatarClick = { resolver.image(room?.avatarMxc, room?.name)?.let(onOpenMedia) },
                )
            },
        )
    }
}

@Composable
private fun LiveTimeline(
    timeline: TimelineState,
    list: LazyListState,
    followNext: FollowRequest,
    resolver: MediaResolver,
    actions: TimelineActions,
    highlighted: String?,
    onLoadOlder: () -> Unit,
) {
    val items = timeline.items
    Box(Modifier.fillMaxSize()) {
        when {
            items == null -> {
                Loading(Modifier.align(Alignment.Center))
            }

            items.isEmpty() && !timeline.hasMoreBefore -> {
                Text(
                    stringResource(R.string.empty_timeline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                FollowNewest(items, list, followNext)
                LoadOlderNearTop(list, timeline, onLoadOlder)
                Timeline(items, list, resolver, actions, highlighted, loadingOlder = timeline.loadingOlder)
            }
        }
    }
}

@Composable
private fun ContextTimeline(
    context: ContextView,
    list: LazyListState,
    resolver: MediaResolver,
    actions: TimelineActions,
    highlighted: String?,
    onLeaveContext: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val items = context.items
        if (items == null) {
            Loading(Modifier.align(Alignment.Center))
        } else {
            Timeline(items, list, resolver, actions, highlighted, loadingOlder = false)
        }
        ExtendedFloatingActionButton(
            onClick = onLeaveContext,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { Text(stringResource(R.string.jump_to_latest)) }
    }
}

@Composable
private fun Timeline(
    items: List<TimelineItem>,
    list: LazyListState,
    resolver: MediaResolver,
    actions: TimelineActions,
    highlighted: String?,
    loadingOlder: Boolean,
) {
    // Newest first + reverseLayout: the list sits on the bottom and new messages push up from there.
    LazyColumn(
        state = list,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.key }, contentType = { it::class }) { item ->
            val lit = item.eventId != null && item.eventId == highlighted
            when (item) {
                is TimelineItem.Message -> MessageRow(item, resolver, actions, Modifier.animateItem(), lit)
                is TimelineItem.StateChange -> StateChangeRow(item, resolver, actions, Modifier.animateItem(), lit)
                is TimelineItem.DaySeparator -> DayRow(item.day, Modifier.animateItem())
            }
        }
        if (loadingOlder) {
            item(key = "loading-older", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Loading() }
            }
        }
    }
}

/**
 * While the newest items are on screen, the room counts as read up to its newest message that
 * someone else sent and the homeserver has (a real event ID).
 */
@Composable
private fun MarkReadAtBottom(
    items: List<TimelineItem>?,
    list: LazyListState,
    onMarkRead: (String) -> Unit,
) {
    val newest =
        items
            ?.asSequence()
            ?.filterIsInstance<TimelineItem.Message>()
            ?.firstOrNull { !it.fromMe && it.eventId.startsWith("$") }
            ?.eventId
    val mark by rememberUpdatedState(onMarkRead)
    LaunchedEffect(newest, list) {
        val target = newest ?: return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex <= 1 }.first { it }
        mark(target)
    }
}

/** Set when we send: the next new message is ours, so the list follows it wherever it was. */
private class FollowRequest {
    var requested = false
}

/**
 * New messages arrive at index 0 (the bottom). The list keeps its first visible item in place, so
 * on its own it would leave them just out of view: follow them while already at the bottom, and
 * always right after we sent one.
 */
@Composable
private fun FollowNewest(
    items: List<TimelineItem>,
    list: LazyListState,
    followNext: FollowRequest,
) {
    val newest = items.firstOrNull()?.key
    LaunchedEffect(newest) {
        if (followNext.requested || list.firstVisibleItemIndex <= 1) {
            list.animateScrollToItem(0)
            // Only once there: our message changes key twice in quick succession (outbox entry,
            // then gomuks' local echo), and the second change cancels the first scroll.
            followNext.requested = false
        }
    }
}

/**
 * Asks for the previous page while the oldest loaded items are within reach — re-checked after
 * every page (keyed on the raw event count), since a page of only hidden events (reactions,
 * redactions, edits) adds nothing visible and wouldn't otherwise move the list.
 */
@Composable
private fun LoadOlderNearTop(
    list: LazyListState,
    timeline: TimelineState,
    onLoadOlder: () -> Unit,
) {
    val load by rememberUpdatedState(onLoadOlder)
    LaunchedEffect(list, timeline.hasMoreBefore, timeline.loadingOlder, timeline.loadedEvents) {
        if (!timeline.hasMoreBefore || timeline.loadingOlder) return@LaunchedEffect
        snapshotFlow {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            last >= info.totalItemsCount - PREFETCH_DISTANCE
        }.first { it }
        load()
    }
}

/** Once a context window is in, scroll to its event and light it up; if it failed, say so and go back. */
@Composable
private fun JumpEffects(
    context: ContextView?,
    list: LazyListState,
    onLeaveContext: () -> Unit,
    highlight: (String) -> Unit,
) {
    val androidContext = LocalContext.current
    val failedText = stringResource(R.string.context_failed)
    val leave by rememberUpdatedState(onLeaveContext)
    val light by rememberUpdatedState(highlight)
    LaunchedEffect(context?.eventId, context?.items != null, context?.failed) {
        val view = context ?: return@LaunchedEffect
        if (view.failed) {
            Toast.makeText(androidContext, failedText, Toast.LENGTH_SHORT).show()
            leave()
            return@LaunchedEffect
        }
        val index = view.items?.indexOfEvent(view.eventId) ?: return@LaunchedEffect
        if (index >= 0) {
            list.scrollToItem(index, list.focusOffset())
            light(view.eventId)
        }
    }
}

/**
 * Where a jumped-to item should sit: a third of the way up rather than on the bottom edge (in this
 * reversed list, "scroll to item" puts it at the start, i.e. the bottom). Negative = further up.
 */
private fun LazyListState.focusOffset() = -(layoutInfo.viewportSize.height / FOCUS_FRACTION)

private val TimelineItem.eventId: String?
    get() =
        when (this) {
            is TimelineItem.Message -> eventId
            is TimelineItem.StateChange -> eventId
            is TimelineItem.DaySeparator -> null
        }

private fun List<TimelineItem>.indexOfEvent(eventId: String) = indexOfFirst { it.eventId == eventId }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading(modifier: Modifier = Modifier) = LoadingIndicator(modifier)

@Composable
private fun DayRow(
    day: LocalDate,
    modifier: Modifier = Modifier,
) {
    val text = remember(day) { day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun typingText(typing: List<String>): String? =
    when (typing.size) {
        0 -> null
        1 -> stringResource(R.string.typing_one, typing[0])
        2 -> stringResource(R.string.typing_two, typing[0], typing[1])
        else -> stringResource(R.string.typing_many)
    }

private const val PREFETCH_DISTANCE = 10
private const val HIGHLIGHT_MS = 1_600L
private const val FOCUS_FRACTION = 3
