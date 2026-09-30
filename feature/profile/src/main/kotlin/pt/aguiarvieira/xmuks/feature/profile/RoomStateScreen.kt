package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

@Composable
fun RoomStateRoute(
    roomId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomStateViewModel =
        hiltViewModel<RoomStateViewModel, RoomStateViewModel.Factory>(key = "state:$roomId") { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RoomStateScreen(state, onBack, modifier)
}

/** A room's state events, raw: by type, each opening to the event as JSON (selectable, to copy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomStateScreen(
    state: RoomStateView,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                    title = { Text(stringResource(R.string.room_state)) },
                )
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
            val groups = state.groups
            when {
                groups == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                state.error != null -> {
                    Text(state.error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    StateList(groups)
                }
            }
        }
    }
}

@Composable
private fun StateList(groups: Map<String, List<StateEntry>>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        groups.forEach { (type, entries) ->
            item(key = "type:$type") {
                Text(
                    type,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { "e:${it.type}\u0000${it.stateKey}" }) { StateRow(it) }
        }
    }
}

/** One event: its state key and sender; tapped, the whole event. */
@Composable
private fun StateRow(entry: StateEntry) {
    var open by rememberSaveable(entry.type, entry.stateKey) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            entry.stateKey.ifEmpty { stringResource(R.string.room_state_empty_key) },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.sender,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (open) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                SelectionContainer {
                    Text(
                        entry.json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
                    )
                }
            }
        }
    }
}
