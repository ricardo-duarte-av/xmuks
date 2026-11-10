package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.designsystem.component.HeaderTitle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader

@Composable
fun SpaceRoute(
    spaceId: String,
    onBack: () -> Unit,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
    /** A tab tapped on the bar: back to home, on that tab. */
    onTabChange: (HomeTab) -> Unit = {},
    /** Shows a room read-only, without marking it read (its long-press menu). */
    onPreviewRoom: (roomId: String, scope: String) -> Unit = { _, _ -> },
    /** A room of the space we're not in: its preview, to join from. */
    onOpenToJoin: (roomId: String, via: List<String>) -> Unit = { _, _ -> },
    viewModel: SpaceViewModel =
        hiltViewModel<SpaceViewModel, SpaceViewModel.Factory>(key = spaceId) { it.create(spaceId) },
) {
    val space by viewModel.space.collectAsStateWithLifecycle()
    val subspaces by viewModel.subspaces.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val editRows by viewModel.editRows.collectAsStateWithLifecycle()
    val unjoined by viewModel.unjoined.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice ->
            val text =
                when (notice) {
                    SpaceEditNotice.Saved -> {
                        resources.getString(R.string.space_edit_saved)
                    }

                    is SpaceEditNotice.Failed -> {
                        resources.getQuantityString(R.plurals.space_edit_failed, notice.count, notice.count)
                    }

                    is SpaceEditNotice.ParentKept -> {
                        resources.getQuantityString(R.plurals.space_edit_parent_kept, notice.count, notice.count)
                    }
                }
            snackbar.showSnackbar(text)
        }
    }
    CompositionLocalProvider(LocalRoomListDisplay provides display) {
        SpaceScreen(
            spaceId = spaceId,
            space = space,
            subspaces = subspaces,
            filter = filter,
            rooms = rooms,
            onSelect = viewModel::select,
            onBack = onBack,
            onOpenRoom = onOpenRoom,
            modifier = modifier,
            badges = badges,
            onTabChange = onTabChange,
            onPreviewRoom = onPreviewRoom,
            editor =
                SpaceEditorState(
                    canEdit = canEdit,
                    session = edit,
                    rows = editRows,
                    query = viewModel.editQuery,
                    onEdit = viewModel::startEditing,
                    onToggle = viewModel::toggle,
                    onCancel = viewModel::cancelEditing,
                    onSave = viewModel::save,
                ),
            snackbar = snackbar,
            more = unjoined,
            moreActions =
                SpaceMoreActions(
                    onOpen = onOpenToJoin,
                    onLoadMore = viewModel::loadMoreUnjoined,
                    onRetry = viewModel::retryUnjoined,
                ),
            avatar = viewModel.media::avatar,
        )
    }
}

/** Everything the space screen's editor needs: whether it may open, its session, its list. */
class SpaceEditorState(
    val canEdit: Boolean = false,
    val session: SpaceEditSession? = null,
    val rows: List<SpaceEditRow>? = null,
    val query: TextFieldState = TextFieldState(),
    val onEdit: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onSave: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpaceScreen(
    spaceId: String,
    space: SpaceSummary?,
    subspaces: List<SpaceSummary>,
    filter: String?,
    rooms: List<RoomSummary>?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
    badges: TabBadges = TabBadges(),
    onTabChange: (HomeTab) -> Unit = {},
    onPreviewRoom: (roomId: String, scope: String) -> Unit = { _, _ -> },
    /** Adding and removing rooms, in place: the list turns into the editor. */
    editor: SpaceEditorState = remember { SpaceEditorState() },
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** The space's rooms we're not in, below the rest. */
    more: SpaceMoreState = SpaceMoreState(),
    moreActions: SpaceMoreActions = remember { SpaceMoreActions() },
    avatar: (String?) -> String? = { null },
) {
    val session = editor.session
    val editing = session != null
    // Back leaves the editor, never the space, and sends nothing.
    BackHandler(enabled = editing, onBack = editor.onCancel)
    val motion = MaterialTheme.motionScheme
    // Cards on a tinted ground, like the room and home: header (with subspace chips), rooms, and
    // home's tab bar, still on Spaces.
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenHeader {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            AnimatedContent(editing, label = "space nav") { edit ->
                                if (edit) {
                                    IconButton(onClick = editor.onCancel) {
                                        Icon(
                                            painterResource(R.drawable.ic_close),
                                            contentDescription = stringResource(R.string.space_edit_cancel),
                                        )
                                    }
                                } else {
                                    BackButton(onBack)
                                }
                            }
                        },
                        title = {
                            val target =
                                session?.targetId?.let { id ->
                                    if (id == spaceId) space?.name else subspaces.firstOrNull { it.roomId == id }?.name
                                }
                            HeaderTitle(
                                id = spaceId,
                                name = space?.name ?: "",
                                avatarUrl = space?.avatarUrl,
                                sharedScope = SharedScopes.SPACES,
                                subtitle = target?.let { stringResource(R.string.space_edit_editing, it) },
                            )
                        },
                        actions = {
                            if (editor.canEdit || editing) SpaceEditAction(session, editor.onEdit, editor.onSave)
                        },
                    )
                    if (subspaces.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            item(key = "all") {
                                FilterChip(
                                    selected = filter == null,
                                    onClick = { onSelect(null) },
                                    label = { Text(stringResource(R.string.space_all)) },
                                    // The editor works on the chip that was selected: no switching under it.
                                    enabled = !editing || filter == null,
                                )
                            }
                            items(subspaces, key = { it.roomId }) { sub ->
                                FilterChip(
                                    selected = filter == sub.roomId,
                                    onClick = { onSelect(if (filter == sub.roomId) null else sub.roomId) },
                                    label = { Text(sub.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    enabled = !editing || filter == sub.roomId,
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // The editor has the whole height; the tabs come back when it closes.
            AnimatedVisibility(
                !editing,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it },
                exit = slideOutVertically(motion.defaultSpatialSpec()) { it },
            ) {
                TabBottomBar(HomeTab.Spaces, badges, onTabChange)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        TabContent(
            ScreenCards.cards,
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AnimatedContent(
                editing,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "space editor",
            ) { edit ->
                if (edit) {
                    SpaceEditList(
                        editor.rows,
                        session?.selected.orEmpty(),
                        editor.query,
                        editor.onToggle,
                        enabled = session?.saving != true,
                    )
                } else {
                    SpaceRoomList(spaceId, filter, rooms, now, onOpenRoom, onPreviewRoom, more, moreActions, avatar)
                }
            }
        }
    }
}

/** The space's rooms, then "More in this space": open unless long, the choice kept per chip. */
@Composable
private fun SpaceRoomList(
    spaceId: String,
    filter: String?,
    rooms: List<RoomSummary>?,
    now: Long,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    onPreviewRoom: (roomId: String, scope: String) -> Unit,
    more: SpaceMoreState,
    moreActions: SpaceMoreActions,
    avatar: (String?) -> String?,
) {
    var folded by rememberSaveable(filter) { mutableStateOf<Boolean?>(null) }
    val expanded = folded?.not() ?: (!more.hasMore && more.rooms.size <= FOLD_ABOVE)
    RoomList(
        rooms,
        now,
        R.string.empty_space,
        SharedScopes.space(spaceId),
        onOpenRoom,
        onPreviewRoom = onPreviewRoom,
        footer =
            if (more.visible) {
                {
                    spaceMoreSection(
                        more,
                        expanded,
                        avatar,
                        SpaceMoreActions(
                            onOpen = moreActions.onOpen,
                            onToggle = { folded = expanded },
                            onLoadMore = moreActions.onLoadMore,
                            onRetry = moreActions.onRetry,
                        ),
                    )
                }
            } else {
                null
            },
    )
}

/** Above this many, "More in this space" starts folded. */
private const val FOLD_ABOVE = 20

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
    }
}
