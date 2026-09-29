package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

@Composable
fun RoomMembersRoute(
    roomId: String,
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomInfoViewModel =
        hiltViewModel<RoomInfoViewModel, RoomInfoViewModel.Factory>(key = roomId) { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val error by viewModel.tasks.error.collectAsStateWithLifecycle()
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    val actions =
        remember(viewModel, onOpenUser) {
            RoomInfoActions(setLevel = viewModel::setLevel, membership = viewModel::membership, openUser = onOpenUser)
        }
    RoomMembersScreen(
        info = (state as? RoomInfoState.Loaded)?.info,
        me = me.orEmpty(),
        media = media,
        actions = actions,
        busy = busy,
        error = error,
        onErrorShow = viewModel.tasks::errorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * A room's members, in a list of their own: grouped by power level (creators first), after anyone
 * asking to join, before the invited. Search narrows every group; the list only ever composes the
 * rows on screen, so rooms of thousands scroll as smoothly as small ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomMembersScreen(
    info: RoomInfo?,
    me: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
    busy: Boolean,
    error: String?,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val search = rememberTextFieldState()
    var inviting by rememberSaveable { mutableStateOf(false) }
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
                        title = { Text(stringResource(R.string.members)) },
                        actions = {
                            if (info?.powerLevels?.canInvite(me) == true) {
                                IconButton(onClick = { inviting = true }) {
                                    Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.invite))
                                }
                            }
                        },
                    )
                    SearchField(search)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
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
            if (info == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                MemberList(info, me, search.text.toString(), media, actions)
            }
        }
    }
    if (inviting) {
        InviteDialog(
            onInvite = { actions.membership(it, MembershipAction.Invite, null) },
            onDismiss = { inviting = false },
        )
    }
}

@Composable
private fun SearchField(search: TextFieldState) {
    OutlinedTextField(
        state = search,
        placeholder = { Text(stringResource(R.string.members_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )
}

@Composable
private fun MemberList(
    info: RoomInfo,
    me: String,
    query: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
) {
    val groups = remember(info, query) { groupMembers(info, query) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        groups.forEach { group ->
            stickyHeader(key = group.key, contentType = "heading") {
                Text(
                    groupTitle(group, info.powerLevels.usersDefault),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(group.members, key = { "${group.key}/${it.userId}" }, contentType = { "member" }) { member ->
                MemberRow(member, info, me, media, actions)
            }
        }
    }
}
