package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.rooms.Invite
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InviteRoute(
    roomId: String,
    onBack: () -> Unit,
    /** Accepted: the room opens in this screen's place. */
    onOpenRoom: (roomId: String) -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteViewModel =
        hiltViewModel<InviteViewModel, InviteViewModel.Factory>(key = roomId) { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shared by viewModel.shared.collectAsStateWithLifecycle()
    val answer by viewModel.answer.collectAsStateWithLifecycle()
    val openRoom by rememberUpdatedState(onOpenRoom)
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(answer) {
        when (val now = answer) {
            is InviteAnswer.Joined -> openRoom(now.roomId)
            InviteAnswer.Declined -> back()
            else -> Unit
        }
    }
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    InviteScreen(
        state = state,
        shared = shared,
        answer = answer,
        media = media,
        actions =
            InviteActions(
                accept = viewModel::accept,
                decline = viewModel::decline,
                declineAndIgnore = viewModel::declineAndIgnore,
                openUser = onOpenUser,
                openRoom = onOpenRoom,
            ),
        onErrorShow = viewModel::errorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/** What can be done from an invite. */
class InviteActions(
    val accept: () -> Unit = {},
    val decline: () -> Unit = {},
    val declineAndIgnore: () -> Unit = {},
    val openUser: (userId: String) -> Unit = {},
    /** A room we share with the inviter. */
    val openRoom: (roomId: String) -> Unit = {},
)

/**
 * An invite, before answering it: what it is (a DM, a room, a space; encrypted or not; how many are
 * in it), who sent it and why, the rooms we already share with them, and accept / decline /
 * decline and ignore them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    state: InviteState,
    shared: SharedRooms,
    answer: InviteAnswer,
    media: ProfileMedia,
    actions: InviteActions,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = (answer as? InviteAnswer.Failed)?.let { stringResource(R.string.action_failed, it.message) }
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
                    title = { Text(stringResource(R.string.invite_title)) },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = ScreenCards.Gap)
                .navigationBarsPadding()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ScreenCards.Gap),
        ) {
            when (state) {
                InviteState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                InviteState.Gone -> {
                    ScreenCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.invite_gone),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }

                is InviteState.Pending -> {
                    val invite = state.invite
                    // Everything fits on one screen with the details folded away; the answers stay
                    // at the bottom, and only unfolded details ever scroll.
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ScreenCards.Gap),
                    ) {
                        InviteCard(invite, media)
                        InviterCard(invite, media, actions.openUser)
                        DetailsCard(invite)
                        SharedCard(invite, shared, actions.openRoom)
                    }
                    AnswerCard(invite, answer, actions)
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: Invite,
    media: ProfileMedia,
) {
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoomAvatar(invite.name, invite.roomId, media.thumbnail(invite.avatarMxc), size = 96.dp)
            Text(invite.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            invite.canonicalAlias?.takeIf { it != invite.name }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                listOf(
                    stringResource(
                        when {
                            invite.isSpace -> R.string.invite_kind_space
                            invite.isDirect -> R.string.invite_kind_dm
                            else -> R.string.invite_kind_room
                        },
                    ),
                    stringResource(
                        if (invite.encryption !=
                            null
                        ) {
                            R.string.room_encrypted
                        } else {
                            R.string.room_not_encrypted
                        }
                    ),
                    pluralStringResource(R.plurals.members_joined, invite.joinedMembers, invite.joinedMembers),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            invite.topic?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
    }
}

/** Who sent it (opens their profile), when, and why if they said. */
@Composable
private fun InviterCard(
    invite: Invite,
    media: ProfileMedia,
    onOpenUser: (String) -> Unit,
) {
    val inviterId = invite.inviterId ?: return
    val name = invite.inviterName ?: inviterId
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Heading(R.string.invite_from)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenUser(
                            inviterId
                        )
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoomAvatar(name, inviterId, media.thumbnail(invite.inviterAvatarMxc), size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        inviterId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            invite.reason?.let {
                Text(
                    stringResource(R.string.invite_reason, it),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (invite.createdAt > 0) {
                Text(
                    stringResource(R.string.invite_when, remember(invite.createdAt) { dateTime(invite.createdAt) }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The room's settings, as far as the invite shows them. */
@Composable
private fun DetailsCard(invite: Invite) {
    val summary =
        listOfNotNull(
            stringResource(JOIN_RULES[invite.joinRule] ?: R.string.join_private),
            invite.roomVersion?.let { stringResource(R.string.room_version, it) },
        ).joinToString(" · ")
    Folding(stringResource(R.string.invite_details), summary) {
        Column {
            Detail(
                R.string.invite_encryption,
                invite.encryption ?: stringResource(R.string.room_not_encrypted),
                monospace = invite.encryption != null,
            )
            Detail(R.string.room_access, stringResource(JOIN_RULES[invite.joinRule] ?: R.string.join_private))
            invite.roomVersion?.let { Detail(R.string.invite_room_version, it) }
            // A word joiner keeps the sigil on the ID's first line instead of alone on its own.
            Detail(R.string.invite_room_id, invite.roomId.replaceFirst("!", "!\u2060"), monospace = true, small = true)
            Text(
                stringResource(R.string.invite_unverified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** The rooms we're already in with the inviter: a stranger has none. Folded, it says how many. */
@Composable
private fun SharedCard(
    invite: Invite,
    shared: SharedRooms,
    onOpenRoom: (String) -> Unit,
) {
    val inviter = invite.inviterId ?: return
    val count =
        when (shared) {
            SharedRooms.Loading -> {
                "…"
            }

            SharedRooms.Unknown -> {
                stringResource(R.string.invite_shared_count_unknown)
            }

            is SharedRooms.Loaded -> {
                val n = shared.rooms.size
                pluralStringResource(R.plurals.invite_shared_rooms, n, n)
            }
        }
    Folding(stringResource(R.string.invite_shared, invite.inviterName ?: inviter), count) {
        when (shared) {
            SharedRooms.Loading -> {
                CircularProgressIndicator(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).size(20.dp))
            }

            SharedRooms.Unknown -> {
                Note(R.string.invite_shared_unknown)
            }

            is SharedRooms.Loaded -> {
                Column {
                    if (shared.rooms.isEmpty()) Note(R.string.invite_shared_none)
                    shared.rooms.forEach { SharedRoomRow(it, onOpenRoom) }
                }
            }
        }
    }
}

/** A card that folds: its [title] and [summary] always, [content] when unfolded (folded at first). */
@Composable
private fun Folding(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) QUARTER_TURN else 0f, label = "fold")
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (!open) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    stringResource(if (open) R.string.fold else R.string.unfold),
                    Modifier.rotate(turn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) Box(Modifier.padding(bottom = 12.dp)) { content() }
        }
    }
}

private const val QUARTER_TURN = 90f

@Composable
private fun SharedRoomRow(
    room: RoomSummary,
    onOpenRoom: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenRoom(room.roomId) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoomAvatar(room.name, room.roomId, room.avatarUrl, size = 32.dp)
        Text(room.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnswerCard(
    invite: Invite,
    answer: InviteAnswer,
    actions: InviteActions,
) {
    val working = answer == InviteAnswer.Working
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.accept, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                if (working) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.accept_invite))
            }
            OutlinedButton(onClick = actions.decline, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.invite_decline))
            }
            invite.inviterId?.let { inviter ->
                TextButton(
                    onClick = actions.declineAndIgnore,
                    enabled = !working,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.invite_decline_ignore, invite.inviterName ?: inviter)) }
            }
        }
    }
}

@Composable
private fun Heading(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun Detail(
    label: Int,
    value: String,
    monospace: Boolean = false,
    small: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

private fun dateTime(timestamp: Long): String =
    Instant
        .ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
