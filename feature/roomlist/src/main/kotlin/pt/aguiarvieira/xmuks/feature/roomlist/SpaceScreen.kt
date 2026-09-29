package pt.aguiarvieira.xmuks.feature.roomlist

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.HeaderTitle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

@Composable
fun SpaceRoute(
    spaceId: String,
    onBack: () -> Unit,
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpaceViewModel =
        hiltViewModel<SpaceViewModel, SpaceViewModel.Factory>(key = spaceId) { it.create(spaceId) },
) {
    val space by viewModel.space.collectAsStateWithLifecycle()
    val subspaces by viewModel.subspaces.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
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
        )
    }
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
    onOpenRoom: (roomId: String, scope: String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
) {
    // Two cards on a tinted ground, like the room and home: header (with subspace chips), rooms.
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = { BackButton(onBack) },
                        title = {
                            HeaderTitle(
                                id = spaceId,
                                name = space?.name ?: "",
                                avatarUrl = space?.avatarUrl,
                                sharedScope = SharedScopes.SPACES,
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
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(padding)
                .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .fillMaxSize(),
        ) {
            RoomList(rooms, now, R.string.empty_space, SharedScopes.space(spaceId), onOpenRoom)
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
    }
}
