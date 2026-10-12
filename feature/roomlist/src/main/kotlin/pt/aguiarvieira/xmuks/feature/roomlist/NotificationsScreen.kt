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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvent
import pt.aguiarvieira.xmuks.core.data.rooms.MentionKind
import pt.aguiarvieira.xmuks.core.data.rooms.Mentions
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps

/** What's shown: the notifications so far, whether more are coming, and why not if it failed. */
data class NotificationsState(
    val kind: MentionKind = MentionKind.Mentions,
    /** The room it was opened from, if any; [roomId] is it while "this room" is on. */
    val openedIn: String? = null,
    /** Only this room's; every room's when null. */
    val roomId: String? = null,
    val items: List<FoundEvent> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel
    @AssistedInject
    constructor(
        @Assisted roomId: String?,
        private val mentions: Mentions,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String?): NotificationsViewModel
        }

        private val _state = MutableStateFlow(NotificationsState(openedIn = roomId, roomId = roomId))
        val state: StateFlow<NotificationsState> = _state.asStateFlow()
        private var running: Job? = null

        init {
            loadMore()
        }

        fun show(kind: MentionKind) {
            if (kind == _state.value.kind) return
            restart { it.copy(kind = kind) }
        }

        fun thisRoomOnly(on: Boolean) = restart { it.copy(roomId = if (on) it.openedIn else null) }

        /** From the top, with what's asked for changed; a page still coming for the old ask is dropped. */
        private fun restart(change: (NotificationsState) -> NotificationsState) {
            running?.cancel()
            _state.update { change(it).copy(items = emptyList(), loading = true, hasMore = true, error = null) }
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
            running =
                viewModelScope.launch {
                    mentions
                        .page(now.kind, before, roomId = now.roomId)
                        .onSuccess { page ->
                            _state.update {
                                it.copy(items = it.items + page, loading = false, hasMore = page.size >= PAGE)
                            }
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
    /** Opened from a room: its mentions first, with a chip to see every room's. */
    roomId: String? = null,
    viewModel: NotificationsViewModel =
        hiltViewModel<NotificationsViewModel, NotificationsViewModel.Factory>(key = "notifications:$roomId") {
            it.create(roomId)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state,
        viewModel.media::avatar,
        viewModel::show,
        viewModel::loadMore,
        onOpenEvent,
        onBack,
        modifier,
        onThisRoom = viewModel::thisRoomOnly,
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
    onThisRoom: (Boolean) -> Unit = {},
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
                        if (state.openedIn != null) {
                            val on = state.roomId != null
                            FilterChip(on, { onThisRoom(!on) }, { Text(stringResource(R.string.search_this_room)) })
                        }
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
            FoundEventList(
                state.items,
                state.loading,
                state.hasMore,
                state.error ?: stringResource(R.string.notifications_none),
                now,
                avatar,
                onLoadMore,
                onOpen,
            )
        }
    }
}

private fun kindLabel(kind: MentionKind) =
    when (kind) {
        MentionKind.Mentions -> R.string.notifications_mentions
        MentionKind.All -> R.string.notifications_all
    }
