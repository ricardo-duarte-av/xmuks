package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomPreview
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

@Composable
fun RoomPreviewRoute(
    roomIdOrAlias: String,
    via: List<String>,
    onBack: () -> Unit,
    /** Joined (or already in it): the room opens in this screen's place. */
    onOpenRoom: (roomId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomPreviewViewModel =
        hiltViewModel<RoomPreviewViewModel, RoomPreviewViewModel.Factory>(key = roomIdOrAlias) {
            it.create(roomIdOrAlias, via)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val join by viewModel.join.collectAsStateWithLifecycle()
    val openRoom by rememberUpdatedState(onOpenRoom)
    LaunchedEffect(join) { (join as? JoinState.Joined)?.let { openRoom(it.roomId) } }
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    RoomPreviewScreen(
        roomIdOrAlias = roomIdOrAlias,
        state = state,
        join = join,
        media = media,
        onJoin = { viewModel.join() },
        onKnock = viewModel::knock,
        onErrorShow = viewModel::errorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * A room we're not in, before joining (`get_room_summary`): what it is, how many are in it, and
 * the way in its rules allow — join, accept an invite, or ask to be let in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPreviewScreen(
    roomIdOrAlias: String,
    state: PreviewState,
    join: JoinState,
    media: ProfileMedia,
    onJoin: () -> Unit,
    onKnock: (reason: String?) -> Unit,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = (join as? JoinState.Failed)?.let { stringResource(R.string.action_failed, it.message) }
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
                    title = { Text(stringResource(R.string.preview_title)) },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = ScreenCards.Gap)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ScreenCards.Gap),
        ) {
            when (state) {
                PreviewState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PreviewState.Loaded -> {
                    PreviewCard(state.preview, media)
                    ScreenCard(Modifier.fillMaxWidth()) { JoinOptions(state.preview, join, onJoin, onKnock) }
                }

                is PreviewState.Failed -> {
                    ScreenCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(roomIdOrAlias, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.preview_failed, state.message))
                            JoinButton(R.string.preview_try_join, join, onJoin)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: RoomPreview,
    media: ProfileMedia,
) {
    val name = preview.name ?: preview.canonicalAlias ?: preview.roomId
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoomAvatar(name, preview.roomId, media.thumbnail(preview.avatarMxc), size = 96.dp)
            Text(name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            preview.canonicalAlias?.takeIf { it != name }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                listOfNotNull(
                    stringResource(R.string.preview_space).takeIf { preview.isSpace },
                    pluralStringResource(R.plurals.members_joined, preview.joinedMembers, preview.joinedMembers),
                    stringResource(R.string.room_encrypted).takeIf { preview.encrypted },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            preview.topic?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
    }
}

/** The ways in the room's rules and our membership allow, or why there's none. */
@Composable
private fun JoinOptions(
    preview: RoomPreview,
    join: JoinState,
    onJoin: () -> Unit,
    onKnock: (reason: String?) -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val invited = preview.membership == "invite"
        val open = preview.joinRule in OPEN_RULES
        when {
            preview.membership == "ban" -> {
                Note(R.string.preview_banned)
            }

            join == JoinState.Knocked || preview.membership == "knock" -> {
                Note(R.string.preview_knocked)
            }

            invited -> {
                JoinButton(R.string.accept_invite, join, onJoin)
            }

            else -> {
                if (open) JoinButton(R.string.join, join, onJoin)
                if (preview.canKnock) {
                    OutlinedButton(
                        onClick = { asking = true },
                        enabled = join != JoinState.Working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.ask_to_join)) }
                }
                if (!open && !preview.canKnock) Note(R.string.preview_invite_only)
            }
        }
    }
    if (asking) {
        ReasonDialog(
            title = stringResource(R.string.ask_to_join),
            onConfirm = onKnock,
            onDismiss = { asking = false },
        )
    }
}

@Composable
private fun JoinButton(
    label: Int,
    join: JoinState,
    onJoin: () -> Unit,
) {
    Button(onClick = onJoin, enabled = join != JoinState.Working, modifier = Modifier.fillMaxWidth()) {
        if (join == JoinState.Working) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
        }
        Text(stringResource(label))
    }
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Rules that let anyone (or members of the right spaces: the server decides) straight in. */
private val OPEN_RULES = setOf("public", "restricted", "knock_restricted")
