package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.HeaderTitle
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
    val resolver = remember(viewModel) { MediaResolver(viewModel.media::avatar, viewModel.media::media) }
    val context = LocalContext.current
    val uriHandler = remember(context) { SafeUriHandler(context) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        RoomScreen(
            roomId = roomId,
            sharedScope = sharedScope,
            room = room,
            items = items,
            typing = typing,
            loadingOlder = loadingOlder,
            hasMoreBefore = hasMoreBefore,
            resolver = resolver,
            onBack = onBack,
            onLoadOlder = viewModel::loadOlder,
            onOpenMedia = onOpenMedia,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: String,
    sharedScope: String,
    room: RoomSummary?,
    items: List<TimelineItem>?,
    typing: List<String>,
    loadingOlder: Boolean,
    hasMoreBefore: Boolean,
    resolver: MediaResolver,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    HeaderTitle(
                        id = roomId,
                        name = room?.name.orEmpty(),
                        avatarUrl = room?.avatarUrl,
                        sharedScope = sharedScope,
                        subtitle = typingText(typing),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                items == null -> {
                    Loading(Modifier.align(Alignment.Center))
                }

                items.isEmpty() && !hasMoreBefore -> {
                    Text(
                        stringResource(R.string.empty_timeline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    Timeline(items, loadingOlder, hasMoreBefore, resolver, onLoadOlder, onOpenMedia)
                }
            }
        }
    }
}

@Composable
private fun Timeline(
    items: List<TimelineItem>,
    loadingOlder: Boolean,
    hasMoreBefore: Boolean,
    resolver: MediaResolver,
    onLoadOlder: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
) {
    val state = rememberLazyListState()
    LoadOlderNearTop(state, hasMoreBefore, onLoadOlder)
    // Newest first + reverseLayout: the list sits on the bottom and new messages push up from there.
    LazyColumn(
        state = state,
        reverseLayout = true,
        contentPadding = PaddingValues(bottom = 8.dp, top = 8.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        items(items, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is TimelineItem.Message -> MessageRow(item, resolver, onOpenMedia, Modifier.animateItem())
                is TimelineItem.StateChange -> StateChangeRow(item, Modifier.animateItem())
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

/** Asks for the previous page once the oldest loaded items come within reach. */
@Composable
private fun LoadOlderNearTop(
    state: LazyListState,
    hasMoreBefore: Boolean,
    onLoadOlder: () -> Unit,
) {
    val load by rememberUpdatedState(onLoadOlder)
    LaunchedEffect(state, hasMoreBefore) {
        if (!hasMoreBefore) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            last >= info.totalItemsCount - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .filter { it }
            .collect { load() }
    }
}

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
private fun StateChangeRow(
    item: TimelineItem.StateChange,
    modifier: Modifier = Modifier,
) {
    Text(
        changeText(item.actorName, item.change),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

@Composable
private fun changeText(
    actor: String,
    change: Change,
): String =
    when (change) {
        Change.Joined -> stringResource(R.string.change_joined, actor)
        Change.Left -> stringResource(R.string.change_left, actor)
        is Change.Invited -> stringResource(R.string.change_invited, actor, change.target)
        is Change.Kicked -> stringResource(R.string.change_kicked, actor, change.target)
        is Change.Banned -> stringResource(R.string.change_banned, actor, change.target)
        is Change.Renamed -> stringResource(R.string.change_renamed, change.from ?: actor, change.to ?: actor)
        Change.ChangedAvatar -> stringResource(R.string.change_avatar, actor)
        is Change.RoomName -> stringResource(R.string.change_room_name, actor, change.name.orEmpty())
        is Change.RoomTopic -> stringResource(R.string.change_room_topic, actor)
        Change.RoomAvatar -> stringResource(R.string.change_room_avatar, actor)
        Change.RoomCreated -> stringResource(R.string.change_created, actor)
        Change.EncryptionEnabled -> stringResource(R.string.change_encryption, actor)
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
