package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.AvatarKind
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement

@Composable
fun SpaceRoute(
    spaceId: String,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpaceViewModel =
        hiltViewModel<SpaceViewModel, SpaceViewModel.Factory>(key = spaceId) { it.create(spaceId) },
) {
    val space by viewModel.space.collectAsStateWithLifecycle()
    val subspaces by viewModel.subspaces.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    spaceId: String,
    space: SpaceSummary?,
    subspaces: List<SpaceSummary>,
    filter: String?,
    rooms: List<RoomSummary>?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { BackButton(onBack) },
                    title = {
                        HeaderTitle(
                            id = spaceId,
                            name = space?.name ?: "",
                            avatarUrl = space?.avatarUrl,
                            kind = AvatarKind.Space,
                        )
                    },
                )
                if (subspaces.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        item(key = "all") {
                            FilterChip(selected = filter == null, onClick = {
                                onSelect(null)
                            }, label = { Text(stringResource(R.string.space_all)) })
                        }
                        items(subspaces, key = { it.roomId }) { sub ->
                            FilterChip(
                                selected = filter == sub.roomId,
                                onClick = { onSelect(if (filter == sub.roomId) null else sub.roomId) },
                                label = { Text(sub.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        RoomList(rooms, now, R.string.empty_space, onOpenRoom, modifier = Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomRoute(
    roomId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomViewModel = hiltViewModel<RoomViewModel, RoomViewModel.Factory>(key = roomId) { it.create(roomId) },
) {
    val room by viewModel.room.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    HeaderTitle(
                        id = roomId,
                        name = room?.name ?: "",
                        avatarUrl = room?.avatarUrl,
                        kind = if (room?.isDirect == true) AvatarKind.Person else AvatarKind.Room,
                    )
                },
            )
        },
    ) { padding ->
        EmptyState(stringResource(R.string.room_placeholder), modifier = Modifier.padding(padding))
    }
}

/** Avatar + name in an app bar: the landing spot of the list's shared elements. */
@Composable
internal fun HeaderTitle(
    id: String,
    name: String,
    avatarUrl: String?,
    kind: AvatarKind,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(
            name = name,
            id = id,
            avatarUrl = avatarUrl,
            kind = kind,
            size = 40.dp,
            modifier = Modifier.sharedElement(SharedKeys.avatar(id)),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedElement(SharedKeys.title(id)),
        )
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
    }
}
