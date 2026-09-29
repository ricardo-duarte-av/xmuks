package pt.aguiarvieira.xmuks.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.prefs.Pref
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** gomuks' preferences: the global ones ([roomId] null) or one room's. */
@Composable
fun PreferencesRoute(
    roomId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel =
        hiltViewModel<PreferencesViewModel, PreferencesViewModel.Factory>(
            key = roomId ?: "global"
        ) { it.create(roomId) },
) {
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val edits =
        remember(viewModel) {
            PrefEdits(
                setBool = { pref, scope, value -> viewModel.set(pref, scope, value) },
                setNumber = { pref, scope, value -> viewModel.set(pref, scope, value) },
                setChoice = { pref, scope, value -> viewModel.set(pref, scope, value) },
                clear = viewModel::clear,
            )
        }
    PreferencesScreen(roomId != null, layers ?: PrefLayers.EMPTY, edits, error, viewModel::errorShown, onBack, modifier)
}

/**
 * Every preference that can be set in the chosen scope — account or this device; or, for a room,
 * the room or the room on this device — with its value, and where that value comes from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    forRoom: Boolean,
    layers: PrefLayers,
    edits: PrefEdits,
    error: String?,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scopes =
        if (forRoom) {
            listOf(
                PrefScope.RoomAccount,
                PrefScope.RoomDevice
            )
        } else {
            listOf(PrefScope.Account, PrefScope.Device)
        }
    var scope by rememberSaveable { mutableStateOf(scopes.first()) }
    val snackbar = remember { SnackbarHostState() }
    val failed = error?.let { stringResource(R.string.action_failed, it) }
    val errorShown by rememberUpdatedState(onErrorShow)
    LaunchedEffect(failed) {
        if (failed != null) {
            errorShown()
            snackbar.showSnackbar(failed)
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        snackbarHost = { SnackbarHost(snackbar) },
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
                        title = {
                            Text(
                                stringResource(if (forRoom) R.string.room_preferences else R.string.preferences)
                            )
                        },
                    )
                    ScopeChooser(scopes, scope) { scope = it }
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
            PrefList(layers, scope, edits)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeChooser(
    scopes: List<PrefScope>,
    selected: PrefScope,
    onSelect: (PrefScope) -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            scopes.forEachIndexed { index, scope ->
                SegmentedButton(
                    selected = scope == selected,
                    onClick = { onSelect(scope) },
                    shape = SegmentedButtonDefaults.itemShape(index, scopes.size),
                ) { Text(stringResource(scopeName(scope))) }
            }
        }
        Text(
            stringResource(scopeDetail(selected)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PrefList(
    layers: PrefLayers,
    scope: PrefScope,
    edits: PrefEdits,
) {
    val shown = ENTRIES.filter { scope in it.pref.scopes }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        shown.groupBy { it.section }.forEach { (section, entries) ->
            stickyHeader(key = section.name, contentType = "heading") {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(entries, key = { it.pref.key }, contentType = { it.pref::class }) { entry ->
                PrefRow(entry, layers, scope, edits)
            }
        }
    }
}

internal fun scopeName(scope: PrefScope) =
    when (scope) {
        PrefScope.Account -> R.string.scope_account
        PrefScope.Device -> R.string.scope_device
        PrefScope.RoomAccount -> R.string.scope_room
        PrefScope.RoomDevice -> R.string.scope_room_device
    }

private fun scopeDetail(scope: PrefScope) =
    when (scope) {
        PrefScope.Account -> R.string.scope_account_detail
        PrefScope.Device -> R.string.scope_device_detail
        PrefScope.RoomAccount -> R.string.scope_room_detail
        PrefScope.RoomDevice -> R.string.scope_room_device_detail
    }

/** A tap target over a whole row. */
internal fun Modifier.tappable(onClick: (() -> Unit)?) = if (onClick != null) clickable(onClick = onClick) else this
