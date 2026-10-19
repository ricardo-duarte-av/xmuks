package pt.aguiarvieira.xmuks.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** xmuks' own settings: this device's, global (gomuks' preferences are [PreferencesRoute]'s). */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel =
        hiltViewModel<PreferencesViewModel, PreferencesViewModel.Factory>(key = "settings") { it.create(null) },
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
    SettingsScreen(layers ?: PrefLayers.EMPTY, edits, error, viewModel::errorShown, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    layers: PrefLayers,
    edits: PrefEdits,
    error: String?,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                    title = { Text(stringResource(R.string.settings)) },
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
            PrefList(ENTRIES.filter { it.own }, layers, PrefScope.Device, edits, plain = true)
        }
    }
}
