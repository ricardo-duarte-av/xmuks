package pt.aguiarvieira.xmuks.feature.roomlist

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel
import pt.aguiarvieira.xmuks.core.network.ConnectionState

@Composable
fun HomeRoute(
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    onOpenSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val dms by viewModel.dms.collectAsStateWithLifecycle()
    val spaces by viewModel.spaces.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    HomeScreen(
        state = HomeUiState(tab, chats, dms, spaces, connection, refreshing, viewModel.account, profile, badges),
        onTabChange = { tab = it },
        onRefresh = viewModel::refresh,
        onOpenRoom = onOpenRoom,
        onOpenSpace = onOpenSpace,
        onAccountClick = { accountOpen = true },
        modifier = modifier,
        search = viewModel.search,
    )
    if (accountOpen) {
        AccountSheet(
            account = viewModel.account,
            profile = profile,
            connection = connection,
            onLogout = viewModel::logout,
            onDismiss = { accountOpen = false },
        )
    }
}

data class HomeUiState(
    val tab: HomeTab,
    val chats: List<RoomSummary>?,
    val dms: List<RoomSummary>?,
    val spaces: List<SpaceSummary>?,
    val connection: ConnectionState,
    val refreshing: Boolean,
    val account: String,
    val profile: OwnProfile? = null,
    val badges: TabBadges = TabBadges(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onTabChange: (HomeTab) -> Unit,
    onRefresh: () -> Unit,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    onOpenSpace: (String) -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
    search: SearchQueries = remember { SearchQueries() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(state.tab.title)) },
                    actions = { AccountButton(state, onAccountClick) },
                )
                ConnectionIndicator(state.connection)
            }
        },
        bottomBar = {
            ShortNavigationBar {
                HomeTab.entries.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = tab == state.tab,
                        onClick = { onTabChange(tab) },
                        icon = { TabIcon(tab, state.badges) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            // All tabs stay composed (only the selected one is drawn): switching is then just a
            // page change instead of rebuilding a grid of avatars. Measured on a OnePlus 7: entering
            // Spaces cost a ~45-90 ms frame when the grid was recomposed from scratch.
            val pager = rememberPagerState(initialPage = state.tab.ordinal) { HomeTab.entries.size }
            LaunchedEffect(state.tab) { pager.scrollToPage(state.tab.ordinal) }
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                beyondViewportPageCount = HomeTab.entries.size - 1,
                key = { HomeTab.entries[it] },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tab = HomeTab.entries[page]
                val query = search.of(tab)
                Column {
                    SearchField(query, stringResource(tab.searchHint))
                    val searching = query.text.isNotBlank()
                    when (tab) {
                        HomeTab.Chats -> {
                            RoomList(
                                state.chats,
                                now,
                                emptyText(searching, R.string.empty_chats),
                                SharedScopes.CHATS,
                                onOpenRoom
                            )
                        }

                        HomeTab.Dms -> {
                            RoomList(
                                state.dms,
                                now,
                                emptyText(searching, R.string.empty_dms),
                                SharedScopes.DMS,
                                onOpenRoom
                            )
                        }

                        HomeTab.Spaces -> {
                            SpaceGrid(state.spaces, emptyText(searching, R.string.empty_spaces), onOpenSpace)
                        }
                    }
                }
            }
        }
    }
}

/** Our display name and Matrix ID beside our avatar, which opens the account sheet. */
@Composable
private fun AccountButton(
    state: HomeUiState,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        state.profile?.let { profile ->
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 200.dp)) {
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    profile.userId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClick) {
            RoomAvatar(
                name = state.profile?.displayName ?: state.account,
                id = state.profile?.userId ?: state.account,
                avatarUrl = state.profile?.avatarUrl,
                size = 32.dp,
            )
        }
    }
}

private fun SearchQueries.of(tab: HomeTab) =
    when (tab) {
        HomeTab.Chats -> chats
        HomeTab.Dms -> dms
        HomeTab.Spaces -> spaces
    }

private fun emptyText(
    searching: Boolean,
    empty: Int,
) = if (searching) R.string.search_no_match else empty

private val HomeTab.searchHint
    get() =
        when (this) {
            HomeTab.Chats -> R.string.search_chats
            HomeTab.Dms -> R.string.search_dms
            HomeTab.Spaces -> R.string.search_spaces
        }

/** Tab icon with the tab's unread badge: a number (red with mentions) or a quiet dot. */
@Composable
private fun TabIcon(
    tab: HomeTab,
    badges: TabBadges,
) {
    val unread =
        when (tab) {
            HomeTab.Chats -> badges.chats
            HomeTab.Dms -> badges.dms
            HomeTab.Spaces -> badges.spaces
        }
    val (level, count) = unread.level()
    BadgedBox(
        badge = {
            when (level) {
                UnreadLevel.None -> {}

                UnreadLevel.Dot -> {
                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                }

                UnreadLevel.Count -> {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(badgeCount(count)) }
                }

                UnreadLevel.Mention -> {
                    Badge { Text(badgeCount(count)) }
                }
            }
        },
    ) {
        Icon(painterResource(tab.icon), contentDescription = null)
    }
}

private fun badgeCount(count: Int) = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString()

private const val MAX_BADGE = 99

@Composable
internal fun RoomList(
    rooms: List<RoomSummary>?,
    now: Long,
    emptyText: Int,
    sharedScope: String,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        rooms == null -> {
            Box(modifier.fillMaxSize())
        }

        rooms.isEmpty() -> {
            EmptyState(stringResource(emptyText), modifier)
        }

        else -> {
            val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
            LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(rooms, key = { it.roomId }, contentType = { "room" }) { room ->
                    RoomListItem(
                        room = room,
                        now = now,
                        onClick = { onOpenRoom(room.roomId, sharedScope) },
                        is24Hour = is24Hour,
                        sharedScope = sharedScope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceGrid(
    spaces: List<SpaceSummary>?,
    emptyText: Int,
    onOpenSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        spaces == null -> {
            Box(modifier.fillMaxSize())
        }

        spaces.isEmpty() -> {
            EmptyState(stringResource(emptyText), modifier)
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(spaces, key = { it.roomId }) { space ->
                    SpaceTile(space, onClick = { onOpenSpace(space.roomId) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Wall clock that ticks every minute, so "today"/weekday labels roll over while the list is open. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MS)
            value = System.currentTimeMillis()
        }
    }
    return now
}

private const val MINUTE_MS = 60_000L

private val HomeTab.title
    get() =
        when (this) {
            HomeTab.Chats -> R.string.title_chats
            HomeTab.Dms -> R.string.title_dms
            HomeTab.Spaces -> R.string.title_spaces
        }

private val HomeTab.label
    get() =
        when (this) {
            HomeTab.Chats -> R.string.tab_chats
            HomeTab.Dms -> R.string.tab_dms
            HomeTab.Spaces -> R.string.tab_spaces
        }

private val HomeTab.icon
    get() =
        when (this) {
            HomeTab.Chats -> R.drawable.ic_chats
            HomeTab.Dms -> R.drawable.ic_dms
            HomeTab.Spaces -> R.drawable.ic_spaces
        }
