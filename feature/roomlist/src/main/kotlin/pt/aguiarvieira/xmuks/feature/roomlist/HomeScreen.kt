package pt.aguiarvieira.xmuks.feature.roomlist

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pt.aguiarvieira.xmuks.core.data.rooms.Invite
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
fun HomeRoute(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    onOpenSpace: (String) -> Unit,
    onOpenProfile: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onSearchMessages: () -> Unit = {},
    onOpenInvite: (roomId: String) -> Unit = {},
    /** Shows a room read-only, without marking it read (its long-press menu). */
    onPreviewRoom: (roomId: String, scope: String) -> Unit = { _, _ -> },
    /** Our server's room directory; [spaces] when opened from the Spaces tab. */
    onOpenDirectory: (spaces: Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val dms by viewModel.dms.collectAsStateWithLifecycle()
    val spaces by viewModel.spaces.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalRoomListDisplay provides display) {
        HomeScreen(
            state =
                HomeUiState(
                    tab,
                    chats,
                    dms,
                    spaces,
                    connection,
                    refreshing,
                    viewModel.account,
                    profile,
                    badges,
                    invites
                ),
            onTabChange = onTabChange,
            onRefresh = viewModel::refresh,
            onOpenRoom = onOpenRoom,
            onOpenSpace = onOpenSpace,
            onAccountClick = { profile?.userId?.let(onOpenProfile) },
            modifier = modifier,
            onOpenNotifications = onOpenNotifications,
            onSearchMessages = onSearchMessages,
            search = viewModel.search,
            onOpenInvite = onOpenInvite,
            onPreviewRoom = onPreviewRoom,
            onOpenDirectory = onOpenDirectory,
            onReorderSpaces = viewModel::reorderSpaces,
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
    /** Pending invites, newest first; each tab shows its own kind. */
    val invites: List<InviteRow> = emptyList(),
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
    onOpenNotifications: () -> Unit = {},
    onSearchMessages: () -> Unit = {},
    onOpenInvite: (roomId: String) -> Unit = {},
    onPreviewRoom: (roomId: String, scope: String) -> Unit = { _, _ -> },
    onOpenDirectory: (spaces: Boolean) -> Unit = {},
    onReorderSpaces: (ids: List<String>, moved: String) -> Unit = { _, _ -> },
) {
    // Flat: the header (with the tab's filter in it, as a search app bar) and the tab bar on the
    // tinted ground, edge to edge; the tab's content between, on the surface. Cards: three cards on
    // the ground, header, content (the filter at its top) and tab bar. The room's layout matches.
    val cards = ScreenCards.cards
    Scaffold(
        modifier = modifier,
        containerColor = if (cards) ScreenCards.ground else MaterialTheme.colorScheme.surface,
        topBar = {
            ScreenHeader {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(state.tab.title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        AccountChip(state, onAccountClick)
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                // Cards: the filter tops the content card instead.
                if (!cards) {
                    FilterBar(
                        search.of(state.tab),
                        stringResource(state.tab.searchHint),
                        onMentions = onOpenNotifications,
                        onSearch = onSearchMessages,
                        onDirectory = { onOpenDirectory(state.tab == HomeTab.Spaces) },
                    )
                }
                // Under the search, which belongs to the app bar: the band comes and goes at the
                // header's edge without moving the field.
                ConnectionIndicator(state.connection)
            }
        },
        bottomBar = { TabBottomBar(state.tab, state.badges, onTabChange) },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        TabContent(cards, Modifier.padding(padding).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
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
                val fade = rememberFadeThrough()
                LaunchedEffect(state.tab) {
                    if (pager.currentPage != state.tab.ordinal) fade.run { pager.scrollToPage(state.tab.ordinal) }
                }
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = false,
                    beyondViewportPageCount = HomeTab.entries.size - 1,
                    key = { HomeTab.entries[it] },
                    modifier = Modifier.fillMaxSize().fadeThrough(fade),
                ) { page ->
                    val tab = HomeTab.entries[page]
                    val query = search.of(tab)
                    var scrolled by remember { mutableStateOf(false) }
                    Column {
                        if (cards) {
                            FilterBar(
                                query,
                                stringResource(tab.searchHint),
                                onMentions = onOpenNotifications,
                                onSearch = onSearchMessages,
                                onDirectory = { onOpenDirectory(tab == HomeTab.Spaces) },
                                scrolledUnder = scrolled,
                            )
                        }
                        val searching = query.text.isNotBlank()
                        when (tab) {
                            HomeTab.Chats -> {
                                RoomList(
                                    state.chats,
                                    now,
                                    emptyText(searching, R.string.empty_chats),
                                    SharedScopes.CHATS,
                                    onOpenRoom,
                                    invites = state.invites.filterUnless(searching) { !it.isSpace },
                                    onOpenInvite = onOpenInvite,
                                    onPreviewRoom = onPreviewRoom,
                                    onScrolledChange = { scrolled = it },
                                )
                            }

                            HomeTab.Dms -> {
                                RoomList(
                                    state.dms,
                                    now,
                                    emptyText(searching, R.string.empty_dms),
                                    SharedScopes.DMS,
                                    onOpenRoom,
                                    invites = state.invites.filterUnless(searching) { it.isDirect && !it.isSpace },
                                    onOpenInvite = onOpenInvite,
                                    onPreviewRoom = onPreviewRoom,
                                    onScrolledChange = { scrolled = it },
                                )
                            }

                            HomeTab.Spaces -> {
                                SpaceGrid(
                                    state.spaces,
                                    emptyText(searching, R.string.empty_spaces),
                                    onOpenSpace,
                                    invites = state.invites.filterUnless(searching) { it.isSpace },
                                    now = now,
                                    onOpenInvite = onOpenInvite,
                                    onScrolledChange = { scrolled = it },
                                    // A filtered grid isn't the whole order: no moving spaces in it.
                                    onReorder = onReorderSpaces.takeUnless { searching },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A screen's content above the [TabBottomBar]: flat on the surface, or a card [ScreenCards.Gap] from
 * the sides (the bar's card keeps the gap below).
 */
@Composable
internal fun TabContent(
    cards: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = if (cards) {
    ScreenCard(modifier.padding(horizontal = ScreenCards.Gap), content)
} else {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, content = content)
}

/**
 * Our display name and Matrix ID beside our avatar; tapping any of it opens the options (mentions,
 * search and the room directory are on the bar).
 */
@Composable
private fun AccountChip(
    state: HomeUiState,
    onOptions: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.account_options),
                    role = Role.Button,
                    onClick = onOptions
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
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
        RoomAvatar(
            name = state.profile?.displayName ?: state.account,
            id = state.profile?.userId ?: state.account,
            avatarUrl = state.profile?.avatarUrl,
            size = 32.dp,
        )
    }
}

/** Tells [onChange] whether [scrolled] holds, as it changes; false once the list is gone. */
@Composable
private fun ReportScrolled(
    onChange: (Boolean) -> Unit,
    scrolled: () -> Boolean,
) {
    val report by rememberUpdatedState(onChange)
    val read by rememberUpdatedState(scrolled)
    LaunchedEffect(Unit) { snapshotFlow { read() }.collect { report(it) } }
    DisposableEffect(Unit) { onDispose { report(false) } }
}

/** A tab's invites ([keep]); none while searching, which looks for rooms. */
private fun List<InviteRow>.filterUnless(
    searching: Boolean,
    keep: (Invite) -> Boolean,
) = if (searching) emptyList() else filter { keep(it.invite) }

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

@Composable
internal fun RoomList(
    rooms: List<RoomSummary>?,
    now: Long,
    emptyText: Int,
    sharedScope: String,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Waiting for an answer: listed above the rooms. */
    invites: List<InviteRow> = emptyList(),
    onOpenInvite: (roomId: String) -> Unit = {},
    /** Shows a room read-only, from its long-press menu. */
    onPreviewRoom: (roomId: String, scope: String) -> Unit = { _, _ -> },
    /** Whether rows have scrolled up under what's above the list. */
    onScrolledChange: (Boolean) -> Unit = {},
) {
    when {
        rooms == null -> {
            Box(modifier.fillMaxSize())
        }

        rooms.isEmpty() && invites.isEmpty() -> {
            EmptyState(stringResource(emptyText), modifier)
        }

        else -> {
            val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
            // Invites go above the rooms: arriving while the list is at its top, they show there.
            val list = rememberTopAnchoredState(remember(rooms, invites) { rooms to invites })
            ReportScrolled(onScrolledChange) { list.canScrollBackward }
            var menuFor by remember { mutableStateOf<RoomSummary?>(null) }
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (invites.isNotEmpty()) {
                    item(key = "invites", contentType = "heading") { InvitesHeading(Modifier.animateItem()) }
                    items(invites, key = { "invite:" + it.invite.roomId }, contentType = { "invite" }) { row ->
                        InviteListItem(
                            row,
                            now,
                            is24Hour,
                            onClick = { onOpenInvite(row.invite.roomId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(rooms, key = { it.roomId }, contentType = { "room" }) { room ->
                    RoomListItem(
                        room = room,
                        now = now,
                        onClick = { onOpenRoom(room.roomId, sharedScope) },
                        is24Hour = is24Hour,
                        sharedScope = sharedScope,
                        modifier = Modifier.animateItem(),
                        onLongClick = { menuFor = room },
                    )
                }
            }
            menuFor?.let { room ->
                RoomMenuSheet(room, onPreview = { onPreviewRoom(room.roomId, sharedScope) }) { menuFor = null }
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
    invites: List<InviteRow> = emptyList(),
    now: Long = 0,
    onOpenInvite: (roomId: String) -> Unit = {},
    onScrolledChange: (Boolean) -> Unit = {},
    /** Long-press and drag moves a space; null: they stay put. */
    onReorder: ((ids: List<String>, moved: String) -> Unit)? = null,
) {
    when {
        spaces == null -> {
            Box(modifier.fillMaxSize())
        }

        spaces.isEmpty() && invites.isEmpty() -> {
            EmptyState(stringResource(emptyText), modifier)
        }

        else -> {
            val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
            val grid = rememberLazyGridState()
            ReportScrolled(onScrolledChange) { grid.canScrollBackward }
            val haptics = LocalHapticFeedback.current
            // The grid's own copy, moved live under the finger; back in step with [spaces] between drags.
            var order by remember { mutableStateOf(spaces) }
            var dragged by remember { mutableStateOf<String?>(null) }
            // After a drop, the [spaces] of that moment are stale: hold the dropped order until new ones come.
            var stale by remember { mutableStateOf<List<SpaceSummary>?>(null) }
            if (dragged == null && order != spaces && spaces !== stale) {
                order = spaces
                stale = null
            }
            val reorder =
                rememberReorderableLazyGridState(grid) { from, to ->
                    val target = order.indexOfFirst { it.roomId == to.key }
                    val source = order.indexOfFirst { it.roomId == from.key }
                    if (source >= 0 && target >= 0) {
                        order = order.toMutableList().apply { add(target, removeAt(source)) }
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = grid,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                // Space invites run the grid's width, above the spaces.
                if (invites.isNotEmpty()) {
                    item(key = "invites", span = { GridItemSpan(maxLineSpan) }) { InvitesHeading() }
                    items(
                        invites,
                        key = { "invite:" + it.invite.roomId },
                        span = { GridItemSpan(maxLineSpan) }
                    ) { row ->
                        InviteListItem(row, now, is24Hour, onClick = { onOpenInvite(row.invite.roomId) })
                    }
                }
                items(order, key = { it.roomId }) { space ->
                    ReorderableItem(reorder, key = space.roomId, enabled = onReorder != null) { dragging ->
                        val scale by animateFloatAsState(if (dragging) DRAGGED_SCALE else 1f, label = "lift")
                        SpaceTile(
                            space,
                            onClick = { onOpenSpace(space.roomId) },
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }.longPressDraggableHandle(
                                        enabled = onReorder != null,
                                        onDragStarted = {
                                            dragged = space.roomId
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            val ids = order.map { it.roomId }
                                            if (ids != spaces.map { it.roomId }) {
                                                stale = spaces
                                                onReorder?.invoke(ids, space.roomId)
                                            }
                                            dragged = null
                                        },
                                    ),
                        )
                    }
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

/** A dragged space tile, lifted off the grid. */
private const val DRAGGED_SCALE = 1.12f

@Composable
internal fun navigationBarHeight() = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

private val HomeTab.title
    get() =
        when (this) {
            HomeTab.Chats -> R.string.title_chats
            HomeTab.Dms -> R.string.title_dms
            HomeTab.Spaces -> R.string.title_spaces
        }
