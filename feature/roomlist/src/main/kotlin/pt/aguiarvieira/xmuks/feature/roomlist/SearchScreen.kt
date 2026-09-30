package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

@Composable
fun SearchRoute(
    roomId: String?,
    onBack: () -> Unit,
    onOpenEvent: (roomId: String, eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel =
        hiltViewModel<SearchViewModel, SearchViewModel.Factory>(key = "search:$roomId") { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state,
        viewModel.text,
        viewModel.media::avatar,
        SearchOptions(viewModel::thisRoomOnly, viewModel::onServer, viewModel::byTime),
        viewModel::loadMore,
        onOpenEvent,
        onBack,
        modifier,
    )
}

/** Toggling where and how to search. */
class SearchOptions(
    val onThisRoom: (Boolean) -> Unit = {},
    val onServer: (Boolean) -> Unit = {},
    val onByTime: (Boolean) -> Unit = {},
)

/** Message search: a query, where to look (this room, everywhere, the server), and the hits. */
@Composable
fun SearchScreen(
    state: SearchState,
    text: TextFieldState,
    avatar: (String?) -> String?,
    options: SearchOptions,
    onLoadMore: () -> Unit,
    onOpen: (roomId: String, eventId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = rememberNow(),
    autoFocus: Boolean = true,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                        val focus = remember { FocusRequester() }
                        SearchField(
                            text,
                            stringResource(R.string.search_messages),
                            Modifier.weight(1f).focusRequester(focus),
                        )
                        if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                    SearchChips(state, options)
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
            val empty =
                when {
                    state.error != null -> state.error
                    state.query.text.isEmpty() -> stringResource(R.string.search_messages_hint)
                    else -> stringResource(R.string.search_no_results)
                }
            FoundEventList(state.hits, state.loading, state.next != null, empty, now, avatar, onLoadMore, onOpen)
        }
    }
}

@Composable
private fun SearchChips(
    state: SearchState,
    options: SearchOptions,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.openedIn != null) {
            val on = state.query.roomId != null
            FilterChip(on, { options.onThisRoom(!on) }, { Text(stringResource(R.string.search_this_room)) })
        }
        FilterChip(
            state.query.onServer,
            { options.onServer(!state.query.onServer) },
            { Text(stringResource(R.string.search_on_server)) },
        )
        FilterChip(
            state.query.byTime,
            { options.onByTime(!state.query.byTime) },
            { Text(stringResource(R.string.search_newest_first)) },
        )
    }
}
