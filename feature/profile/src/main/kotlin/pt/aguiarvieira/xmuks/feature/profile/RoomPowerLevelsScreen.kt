package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.Threshold
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader

@Composable
fun RoomPowerLevelsRoute(
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
    val actions =
        remember(viewModel, onOpenUser) {
            RoomInfoActions(
                setLevel = viewModel::setLevel,
                setThreshold = { threshold, level -> viewModel.editLevels { it.with(threshold, level) } },
                setEventLevel = { type, level -> viewModel.editLevels { it.withEvent(type, level) } },
                openUser = onOpenUser,
            )
        }
    RoomPowerLevelsScreen(
        info = (state as? RoomInfoState.Loaded)?.info,
        me = me.orEmpty(),
        actions = actions,
        busy = busy,
        error = error,
        onErrorShow = viewModel.tasks::errorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Who may do what in a room: the levels whole kinds of actions need, those of particular event
 * types, and the users given a level of their own. Each is changeable only within our own level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPowerLevelsScreen(
    info: RoomInfo?,
    me: String,
    actions: RoomInfoActions,
    busy: Boolean,
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
            ScreenHeader {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = { Text(stringResource(R.string.power_levels)) },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
    ) { padding ->
        if (info == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PowerLevelCards(info, me, actions, padding)
        }
    }
}

/** What's being changed: a threshold, an event type, a user, or something new of either. */
private const val THRESHOLD = "t:"
private const val EVENT = "e:"
private const val USER = "u:"
private const val ADD_EVENT = "add-event"
private const val ADD_USER = "add-user"

@Composable
private fun PowerLevelCards(
    info: RoomInfo,
    me: String,
    actions: RoomInfoActions,
    padding: PaddingValues,
) {
    val levels = info.powerLevels
    val mine = levels.of(me)
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val card = Modifier.fillMaxWidth().padding(bottom = ScreenCards.Gap)
    LazyColumn(
        modifier = Modifier.padding(padding).padding(horizontal = ScreenCards.Gap).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = ScreenCards.Gap),
    ) {
        item(key = "mine") {
            ScreenCard(card) {
                Text(
                    stringResource(R.string.power_levels_yours, levelName(mine, levels.usersDefault)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        item(key = "thresholds") {
            LevelCard(R.string.power_levels_actions, card) {
                Threshold.entries.forEach { threshold ->
                    val level = levels.of(threshold)
                    LevelRow(
                        title = stringResource(THRESHOLD_LABELS.getValue(threshold)),
                        subtitle = null,
                        level = levelName(level, levels.usersDefault),
                        onClick = { editing = THRESHOLD + threshold.name }.takeIf { levels.canChange(me, level) },
                    )
                }
            }
        }
        item(key = "events") {
            LevelCard(R.string.power_levels_events, card) {
                levels.events.toSortedMap().forEach { (type, level) ->
                    val editable = levels.canChange(me, level)
                    LevelRow(
                        title = EVENT_LABELS[type]?.let { stringResource(it) } ?: type,
                        subtitle = type.takeIf { it in EVENT_LABELS },
                        level = levelName(level, levels.usersDefault),
                        onClick = { editing = EVENT + type }.takeIf { editable },
                        onRemove = { actions.setEventLevel(type, null) }.takeIf { editable },
                    )
                }
                if (levels.canSetState(me, PowerLevels.TYPE)) {
                    AddRow(R.string.power_levels_add_event) { editing = ADD_EVENT }
                }
            }
        }
        item(key = "users") {
            LevelCard(R.string.power_levels_users, card) {
                val creators = levels.creators.sorted().map { it to PowerLevels.CREATOR }
                val users =
                    levels.users.entries.sortedWith(
                        compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }
                    )
                (creators + users.map { it.key to it.value }).forEach { (userId, level) ->
                    val editable = levels.canChangeLevel(me, userId)
                    LevelRow(
                        title = info.member(userId)?.name ?: userId,
                        subtitle = userId,
                        level = levelName(level, levels.usersDefault),
                        onClick = { editing = USER + userId }.takeIf { editable },
                        onRemove =
                            {
                                actions.setLevel(
                                    userId,
                                    levels.usersDefault
                                )
                            }.takeIf { editable && userId != me },
                    )
                }
                if (levels.canSetState(me, PowerLevels.TYPE)) {
                    AddRow(R.string.power_levels_add_user) { editing = ADD_USER }
                }
            }
        }
    }
    LevelEditor(editing ?: return, levels, me, actions) { editing = null }
}

/** The dialog for what [editing] names. */
@Composable
private fun LevelEditor(
    editing: String,
    levels: PowerLevels,
    me: String,
    actions: RoomInfoActions,
    onDone: () -> Unit,
) {
    val mine = levels.of(me)
    when {
        editing.startsWith(THRESHOLD) -> {
            val threshold = Threshold.valueOf(editing.removePrefix(THRESHOLD))
            RoleDialog(
                current = levels.of(threshold),
                mine = mine,
                usersDefault = levels.usersDefault,
                isSelf = false,
                onChoose = { actions.setThreshold(threshold, it) },
                onDismiss = onDone,
                title = stringResource(THRESHOLD_LABELS.getValue(threshold)),
            )
        }

        editing.startsWith(EVENT) -> {
            val type = editing.removePrefix(EVENT)
            RoleDialog(
                current = levels.forEvent(type),
                mine = mine,
                usersDefault = levels.usersDefault,
                isSelf = false,
                onChoose = { actions.setEventLevel(type, it) },
                onDismiss = onDone,
                title = EVENT_LABELS[type]?.let { stringResource(it) } ?: type,
            )
        }

        editing.startsWith(USER) -> {
            val userId = editing.removePrefix(USER)
            RoleDialog(
                current = levels.of(userId),
                mine = mine,
                usersDefault = levels.usersDefault,
                isSelf = userId == me,
                onChoose = { actions.setLevel(userId, it) },
                onDismiss = onDone,
                title = userId,
            )
        }

        editing == ADD_EVENT -> {
            AddLevelDialog(
                title = stringResource(R.string.power_levels_add_event),
                hint = stringResource(R.string.power_levels_event_hint),
                mine = mine,
                initial = minOf(levels.stateDefault, mine),
                valid = { it.isNotEmpty() && ' ' !in it && it !in levels.events },
                onAdd = actions.setEventLevel,
                onDismiss = onDone,
            )
        }

        editing == ADD_USER -> {
            AddLevelDialog(
                title = stringResource(R.string.power_levels_add_user),
                hint = stringResource(R.string.invite_hint),
                mine = mine,
                initial = minOf(PowerLevels.MODERATOR, mine),
                valid = { id ->
                    id.startsWith("@") && ':' in id && ' ' !in id && id !in levels.users && id !in levels.creators
                },
                onAdd = actions.setLevel,
                onDismiss = onDone,
            )
        }
    }
}

@Composable
private fun LevelCard(
    title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ScreenCard(modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            content()
        }
    }
}

/** One level: what it's for, and what it is; tappable (and removable) only when ours to change. */
@Composable
private fun LevelRow(
    title: String,
    subtitle: String?,
    level: String,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)? = null,
) {
    ListItem(
        supportingContent =
            subtitle?.let {
                { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level, style = MaterialTheme.typography.labelLarge)
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) { Text(title) }
}

@Composable
private fun AddRow(
    label: Int,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(painterResource(R.drawable.ic_add), null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(label), color = MaterialTheme.colorScheme.primary) }
}

/** A level as a role and its number ("Moderator · 50"), or just the number when it's no role. */
@Composable
private fun levelName(
    level: Long,
    usersDefault: Long,
): String =
    when (level) {
        PowerLevels.CREATOR -> stringResource(R.string.role_creator)
        PowerLevels.ADMIN -> "${stringResource(R.string.role_admin)} · $level"
        PowerLevels.MODERATOR -> "${stringResource(R.string.role_moderator)} · $level"
        usersDefault -> "${stringResource(R.string.role_user)} · $level"
        else -> level.toString()
    }

private val THRESHOLD_LABELS =
    mapOf(
        Threshold.UsersDefault to R.string.pl_users_default,
        Threshold.EventsDefault to R.string.pl_events_default,
        Threshold.StateDefault to R.string.pl_state_default,
        Threshold.Invite to R.string.pl_invite,
        Threshold.Kick to R.string.pl_kick,
        Threshold.Ban to R.string.pl_ban,
        Threshold.Redact to R.string.pl_redact,
        Threshold.RoomNotification to R.string.pl_room_notification,
    )

/** The event types people usually set levels for, by what they let one do. */
private val EVENT_LABELS =
    mapOf(
        "m.room.name" to R.string.pl_ev_name,
        "m.room.topic" to R.string.pl_ev_topic,
        "m.room.avatar" to R.string.pl_ev_avatar,
        "m.room.canonical_alias" to R.string.pl_ev_alias,
        "m.room.join_rules" to R.string.pl_ev_join_rules,
        "m.room.history_visibility" to R.string.pl_ev_history,
        "m.room.encryption" to R.string.pl_ev_encryption,
        "m.room.power_levels" to R.string.pl_ev_power_levels,
        "m.room.server_acl" to R.string.pl_ev_server_acl,
        "m.room.tombstone" to R.string.pl_ev_tombstone,
        "m.room.pinned_events" to R.string.pl_ev_pinned,
        "m.space.child" to R.string.pl_ev_space_child,
        "m.reaction" to R.string.pl_ev_reaction,
        "m.room.redaction" to R.string.pl_ev_redaction,
        "im.vector.modular.widgets" to R.string.pl_ev_widgets,
    )
