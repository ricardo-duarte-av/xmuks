package pt.aguiarvieira.xmuks.feature.roomlist

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.Mention
import pt.aguiarvieira.xmuks.core.data.rooms.MentionKind
import pt.aguiarvieira.xmuks.core.data.rooms.Mentions
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps
import javax.inject.Inject

/** What's shown: the notifications so far, whether more are coming, and why not if it failed. */
data class NotificationsState(
    val kind: MentionKind = MentionKind.Mentions,
    val items: List<Mention> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val mentions: Mentions,
        val media: MediaUrls,
    ) : ViewModel() {
        private val _state = MutableStateFlow(NotificationsState())
        val state: StateFlow<NotificationsState> = _state.asStateFlow()

        init {
            loadMore()
        }

        fun show(kind: MentionKind) {
            if (kind == _state.value.kind) return
            _state.value = NotificationsState(kind = kind)
            loadMore()
        }

        /** The next page, older than what's shown. */
        fun loadMore() {
            val now = _state.value
            if (now.items.isNotEmpty() && (now.loading || !now.hasMore)) return
            _state.update { it.copy(loading = true, error = null) }
            val before =
                now.items
                    .lastOrNull()
                    ?.timestamp
                    ?.minus(1) ?: System.currentTimeMillis()
            viewModelScope.launch {
                mentions
                    .page(now.kind, before)
                    .onSuccess { page ->
                        _state.update { it.copy(items = it.items + page, loading = false, hasMore = page.size >= PAGE) }
                    }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
            }
        }

        private companion object {
            const val PAGE = 30
        }
    }

@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    onOpenEvent: (roomId: String, eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state,
        viewModel.media::avatar,
        viewModel::show,
        viewModel::loadMore,
        onOpenEvent,
        onBack,
        modifier
    )
}

/** Past notifications across rooms (mentions, or everything that notified), newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsState,
    avatar: (String?) -> String?,
    onKind: (MentionKind) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (roomId: String, eventId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = { Text(stringResource(R.string.notifications)) },
                    )
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MentionKind.entries.forEach { kind ->
                            FilterChip(
                                selected = kind == state.kind,
                                onClick = { onKind(kind) },
                                label = { Text(stringResource(kindLabel(kind))) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(padding)
                .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            NotificationList(state, now, avatar, onLoadMore, onOpen)
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationsState,
    now: Long,
    avatar: (String?) -> String?,
    onLoadMore: () -> Unit,
    onOpen: (roomId: String, eventId: String) -> Unit,
) {
    val list = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            list.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index == list.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore && !state.loading) loadMore() }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    when {
        state.items.isEmpty() && state.loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: stringResource(R.string.notifications_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.items, key = { it.eventId }) { mention ->
                    MentionRow(mention, avatar, ListTimestamps.format(mention.timestamp, now, is24Hour = is24Hour)) {
                        onOpen(mention.roomId, mention.eventId)
                    }
                }
                if (state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** A notification: the room, who said what, and when; opening the room at it. */
@Composable
private fun MentionRow(
    mention: Mention,
    avatar: (String?) -> String?,
    time: String,
    onClick: () -> Unit,
) {
    val roomName = mention.room?.name ?: mention.roomId
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(roomName, mention.roomId, mention.room?.avatarUrl, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    roomName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // In a DM the room is the sender already.
            if (mention.room?.isDirect != true) {
                SenderLine(mention, avatar)
            }
            Text(
                mention.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SenderLine(
    mention: Mention,
    avatar: (String?) -> String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoomAvatar(mention.senderName, mention.sender, avatar(mention.senderAvatarMxc), size = 18.dp)
        Text(
            mention.senderName,
            style = MaterialTheme.typography.labelLarge,
            color = senderColor(mention.sender),
            maxLines = 1,
        )
    }
}

private fun kindLabel(kind: MentionKind) =
    when (kind) {
        MentionKind.Mentions -> R.string.notifications_mentions
        MentionKind.All -> R.string.notifications_all
    }
