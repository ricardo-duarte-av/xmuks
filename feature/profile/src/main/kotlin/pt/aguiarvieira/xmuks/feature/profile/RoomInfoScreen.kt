package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

@Composable
fun RoomInfoRoute(
    roomId: String,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onOpenMembers: () -> Unit,
    /** We left the room: nothing of it to go back to. */
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPreferences: () -> Unit = {},
    viewModel: RoomInfoViewModel =
        hiltViewModel<RoomInfoViewModel, RoomInfoViewModel.Factory>(key = roomId) { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val personas by viewModel.roomPersonas.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.setting.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val error by viewModel.tasks.error.collectAsStateWithLifecycle()
    val notifyError by viewModel.notifications.error.collectAsStateWithLifecycle()
    val left by viewModel.left.collectAsStateWithLifecycle()
    val leave by rememberUpdatedState(onLeft)
    LaunchedEffect(left) { if (left) leave() }
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    val actions =
        remember(viewModel, onOpenUser, onOpenMedia, onOpenMembers, onOpenPreferences) {
            RoomInfoActions(
                setName = viewModel::setName,
                setTopic = viewModel::setTopic,
                setAvatar = viewModel::setAvatar,
                setJoinRule = viewModel::setJoinRule,
                setHistoryVisibility = viewModel::setHistoryVisibility,
                enableEncryption = viewModel::enableEncryption,
                setNotifications = viewModel.notifications::set,
                setLevel = viewModel::setLevel,
                membership = viewModel::membership,
                leave = viewModel::leave,
                personas = PersonaEdits.of(viewModel.personas),
                openUser = onOpenUser,
                openMedia = onOpenMedia,
                openMembers = onOpenMembers,
                openPreferences = onOpenPreferences,
            )
        }
    RoomInfoScreen(
        state = state,
        me = me.orEmpty(),
        notifications = notifications,
        personas = personas,
        media = media,
        actions = actions,
        busy = busy,
        error = error ?: notifyError,
        onErrorShow = {
            viewModel.tasks.errorShown()
            viewModel.notifications.errorShown()
        },
        onRetry = viewModel::refresh,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * A room: its avatar, name, topic and address; how it notifies; its settings (changeable with the
 * power to); our per-message profiles for it; its members, with moderation; and leaving it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomInfoScreen(
    state: RoomInfoState,
    me: String,
    notifications: RoomNotifications,
    personas: PerMessageProfiles,
    media: ProfileMedia,
    actions: RoomInfoActions,
    busy: Boolean,
    error: String?,
    onErrorShow: () -> Unit,
    onRetry: () -> Unit,
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
                                stringResource(R.string.room_info),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
    ) { padding ->
        when (state) {
            RoomInfoState.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is RoomInfoState.Failed -> {
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.room_load_failed, state.message))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }

            is RoomInfoState.Loaded -> {
                RoomInfoCards(state.info, me, notifications, personas, media, actions, padding)
            }
        }
    }
}

@Composable
private fun RoomInfoCards(
    info: RoomInfo,
    me: String,
    notifications: RoomNotifications,
    personas: PerMessageProfiles,
    media: ProfileMedia,
    actions: RoomInfoActions,
    padding: PaddingValues,
) {
    val card = Modifier.fillMaxWidth().padding(bottom = ScreenCards.Gap)
    LazyColumn(
        modifier = Modifier.padding(padding).padding(horizontal = ScreenCards.Gap).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = ScreenCards.Gap),
    ) {
        item(key = "hero") { RoomHeroCard(info, me, media, actions, card) }
        item(key = "settings") { RoomSettingsCard(info, me, notifications, actions, card) }
        item(key = "members") { MembersCard(info, actions.openMembers, card) }
        item(key = "preferences") {
            ScreenCard(card) { LinkRow(R.drawable.ic_tune, R.string.room_preferences, actions.openPreferences) }
        }
        item(key = "personas") {
            PersonasCard(
                personas,
                media,
                actions.personas,
                card,
                title = stringResource(R.string.room_pmp_title),
                explainer = stringResource(R.string.room_pmp_explainer),
            )
        }
        item(key = "leave") { LeaveCard(info, actions.leave, card) }
    }
}
