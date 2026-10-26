package pt.aguiarvieira.xmuks.feature.roomlist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryRoom
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenBody
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader

@Composable
fun DirectoryRoute(
    spaces: Boolean,
    onBack: () -> Unit,
    /** A room we're in. */
    onOpenRoom: (roomId: String) -> Unit,
    /** A room we're not in, to look at and join, found through [via]. */
    onPreviewRoom: (roomId: String, via: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DirectoryViewModel =
        hiltViewModel<DirectoryViewModel, DirectoryViewModel.Factory>(key = "directory:$spaces") { it.create(spaces) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }
    val open = { room: DirectoryRoom ->
        if (room.roomId in state.joined) {
            onOpenRoom(room.roomId)
        } else {
            onPreviewRoom(room.roomId, listOfNotNull(state.server))
        }
    }
    DirectoryScreen(
        state,
        viewModel.text,
        viewModel.media::avatar,
        DirectoryActions(
            onOpen = open,
            onMenu = viewModel::openMenu,
            onSpacesOnly = viewModel::spacesOnly,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::reload,
        ),
        onBack,
        modifier,
    )
    state.menu?.let { menu ->
        DirectoryMenuSheet(
            menu,
            joined = menu.room.roomId in state.joined,
            server = state.server.orEmpty(),
            onOpen = { open(menu.room) },
            onRemove = { viewModel.remove(menu.room) },
            onDismiss = viewModel::closeMenu,
        )
    }
}

/** What the directory's rooms and options do. */
class DirectoryActions(
    val onOpen: (DirectoryRoom) -> Unit = {},
    val onMenu: (DirectoryRoom) -> Unit = {},
    val onSpacesOnly: (Boolean) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Our server's public rooms: searched on the server, a page at a time. */
@Composable
fun DirectoryScreen(
    state: DirectoryState,
    text: TextFieldState,
    avatar: (String?) -> String?,
    actions: DirectoryActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenHeader {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                        SearchField(
                            text,
                            state.server?.let { stringResource(R.string.directory_search, it) }
                                ?: stringResource(R.string.directory_open),
                            Modifier.weight(1f),
                        )
                    }
                    FilterChip(
                        state.spaces,
                        { actions.onSpacesOnly(!state.spaces) },
                        { Text(stringResource(R.string.directory_spaces_only)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        },
    ) { padding ->
        ScreenBody(
            Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            DirectoryList(state, avatar, actions)
        }
    }
}

@Composable
private fun DirectoryList(
    state: DirectoryState,
    avatar: (String?) -> String?,
    actions: DirectoryActions,
) {
    val list = rememberLazyListState()
    val loadMore by rememberUpdatedState(actions.onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            list.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index == list.layoutInfo.totalItemsCount - 1
        }
    }
    val hasMore = state.next != null
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore && !state.loading) loadMore() }
    when {
        state.rooms.isEmpty() && state.loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        state.rooms.isEmpty() -> {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        state.unsupported -> stringResource(R.string.directory_unsupported)
                        state.error != null -> stringResource(R.string.directory_failed, state.error)
                        else -> stringResource(R.string.directory_empty)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.error != null && !state.unsupported) {
                    TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.directory_retry)) }
                }
            }
        }

        else -> {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.rooms, key = { it.roomId }) { room ->
                    DirectoryRow(
                        room,
                        room.roomId in state.joined,
                        avatar,
                        Modifier.combinedClickable(
                            onClick = { actions.onOpen(room) },
                            onLongClick = { actions.onMenu(room) },
                        ),
                    )
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

/** A listed room: its avatar, name, address, topic, and how many are in it (and whether we are). */
@Composable
private fun DirectoryRow(
    room: DirectoryRoom,
    joined: Boolean,
    avatar: (String?) -> String?,
    modifier: Modifier = Modifier,
) {
    val name = room.name ?: room.alias ?: room.roomId
    val members = pluralStringResource(R.plurals.directory_members, room.members, room.members)
    ListItem(
        leadingContent = { RoomAvatar(name, room.roomId, avatar(room.avatarMxc)) },
        overlineContent = { Text(room.alias ?: room.roomId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = room.topic?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Text(
                if (joined) stringResource(R.string.directory_joined, members) else members,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
    ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * A listed room's long-press menu: open (or preview) it, copy its address, take it off the
 * directory. That last is dimmed, not hidden, without the power to; tapping it says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryMenuSheet(
    menu: DirectoryMenu,
    joined: Boolean,
    server: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val room = menu.room
    val name = room.name ?: room.alias ?: room.roomId
    var confirming by rememberSaveable { mutableStateOf(false) }
    val refused = stringResource(R.string.directory_remove_refused)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            MenuItem(
                if (joined) R.drawable.ic_chats else R.drawable.ic_visibility,
                if (joined) R.string.directory_open_room else R.string.directory_preview_room,
            ) {
                onOpen()
                onDismiss()
            }
            MenuItem(R.drawable.ic_copy, R.string.directory_copy_address) {
                clipboard.setText(AnnotatedString(room.alias ?: room.roomId))
                onDismiss()
            }
            val canRemove = menu.canRemove == true
            MenuItem(
                R.drawable.ic_close,
                R.string.directory_remove,
                dimmed = !canRemove,
                trailing = { if (menu.canRemove == null) CircularProgressIndicator(Modifier.padding(4.dp)) },
            ) {
                when (menu.canRemove) {
                    true -> confirming = true
                    false -> Toast.makeText(context, refused, Toast.LENGTH_SHORT).show()
                    null -> Unit
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.directory_remove_confirm, name)) },
            text = { Text(stringResource(R.string.directory_remove_explain, server)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onRemove()
                    onDismiss()
                }) { Text(stringResource(R.string.directory_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun MenuItem(
    icon: Int,
    label: Int,
    dimmed: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors =
        if (dimmed) {
            val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = DIMMED_ALPHA)
            ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = dim, leadingIconColor = dim)
        } else {
            ListItemDefaults.colors(containerColor = Color.Transparent)
        }
    ListItem(
        leadingContent = { Icon(painterResource(icon), null) },
        trailingContent = trailing,
        colors = colors,
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(label)) }
}

private const val DIMMED_ALPHA = 0.38f
