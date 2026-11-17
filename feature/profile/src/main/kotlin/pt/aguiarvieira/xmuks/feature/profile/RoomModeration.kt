package pt.aguiarvieira.xmuks.feature.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.MemberStanding
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.SentEvent
import pt.aguiarvieira.xmuks.core.data.roominfo.canRedact
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard

/** Someone's profile opened from a room: moderating them there. */
@Immutable
class RoomModeration(
    val standing: MemberStanding,
    val me: String,
    val userId: String,
    /** Their name, as the room has it. */
    val name: String,
    /** Their events that could be deleted: null until looked for (and while looking). */
    val recent: List<SentEvent>?,
    val onSetLevel: (Long) -> Unit,
    val onMembership: (MembershipAction, reason: String?) -> Unit,
    val onBan: (reason: String?, deleteRecent: Boolean, keepState: Boolean) -> Unit,
    val onFindRecent: () -> Unit,
    val onDeleteRecent: (keepState: Boolean, reason: String?) -> Unit,
)

/** One thing we may do to them; what it needs ([needed]) and whether we must outrank them for it. */
private enum class Moderation(
    val label: Int,
    val icon: Int,
    val destructive: Boolean,
    val action: MembershipAction?,
) {
    Role(R.string.member_role, R.drawable.ic_shield, false, null),
    Approve(R.string.member_approve, R.drawable.ic_person_add, false, MembershipAction.Invite),
    Invite(R.string.invite, R.drawable.ic_person_add, false, MembershipAction.Invite),
    Deny(R.string.member_deny, R.drawable.ic_person_remove, true, MembershipAction.Kick),
    Revoke(R.string.member_revoke, R.drawable.ic_person_remove, true, MembershipAction.Kick),
    Kick(R.string.member_kick, R.drawable.ic_person_remove, true, MembershipAction.Kick),
    Unban(R.string.member_unban, R.drawable.ic_gavel, false, MembershipAction.Unban),
    Ban(R.string.member_ban, R.drawable.ic_gavel, true, MembershipAction.Ban),
    DeleteRecent(R.string.moderation_delete_recent, R.drawable.ic_delete, true, null),
    ;

    fun needed(levels: PowerLevels): Long =
        when (this) {
            Role -> levels.forState(PowerLevels.TYPE)
            Approve, Invite -> levels.invite
            Deny, Revoke, Kick -> levels.kick
            Unban, Ban -> levels.ban
            DeleteRecent -> levels.redactOthers
        }

    /** Inviting is the only one that doesn't act on them, so needn't outrank them. */
    val outranks: Boolean get() = action != MembershipAction.Invite
}

/** Done at once, as in the member sheet: letting in and unbanning are undone as easily. */
private val IMMEDIATE = setOf(Moderation.Approve, Moderation.Invite, Moderation.Unban)

/** What applies to them as they stand: always their role, then by membership, then a ban and deleting. */
private fun moderationsFor(membership: Membership): List<Moderation> =
    buildList {
        add(Moderation.Role)
        when (membership) {
            Membership.Knock -> addAll(listOf(Moderation.Approve, Moderation.Deny))
            Membership.Invite -> add(Moderation.Revoke)
            Membership.Join -> add(Moderation.Kick)
            Membership.Leave -> add(Moderation.Invite)
            Membership.Ban -> add(Moderation.Unban)
        }
        if (membership != Membership.Ban) add(Moderation.Ban)
        add(Moderation.DeleteRecent)
    }

/**
 * Their role, membership (kick, ban and their undoing) and deleting what they sent recently, in the
 * room the profile was opened from. What we may not do stays, dimmed; tapping it says why.
 */
@Composable
internal fun ModerationCard(
    moderation: RoomModeration,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<Moderation?>(null) }
    val standing = moderation.standing
    val levels = standing.levels
    ScreenCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.moderation_title, standing.roomName ?: stringResource(R.string.this_room)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            moderationsFor(standing.membership).forEach { item ->
                val refusal = refusal(item, levels, moderation.me, moderation.userId, moderation.name)
                ModerationRow(
                    item = item,
                    allowed = refusal == null,
                    supporting =
                        if (item ==
                            Moderation.Role
                        ) {
                            roleName(levels.of(moderation.userId), levels.usersDefault)
                        } else {
                            null
                        },
                ) {
                    when {
                        refusal != null -> {
                            Toast.makeText(context, refusal, Toast.LENGTH_SHORT).show()
                        }

                        item in IMMEDIATE -> {
                            item.action?.let { moderation.onMembership(it, null) }
                        }

                        else -> {
                            open = item
                        }
                    }
                }
            }
        }
    }
    ModerationDialog(open, moderation) { open = null }
}

/** Why we may not do [item] to them, or null when we may. */
@Composable
private fun refusal(
    item: Moderation,
    levels: PowerLevels,
    me: String,
    target: String,
    name: String,
): String? {
    val mine = levels.of(me)
    val theirs = levels.of(target)
    val needed = item.needed(levels)
    return when {
        target in levels.creators -> stringResource(R.string.moderation_refused_creator)
        mine < needed -> stringResource(R.string.moderation_refused_level, needed, mine)
        item.outranks && mine <= theirs -> stringResource(R.string.moderation_refused_outranked, name, theirs, mine)
        else -> null
    }
}

@Composable
private fun ModerationRow(
    item: Moderation,
    allowed: Boolean,
    supporting: String?,
    onClick: () -> Unit,
) {
    val tint =
        when {
            !allowed -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            item.destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    val supportingColor =
        if (allowed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        }
    ListItem(
        leadingContent = { Icon(painterResource(item.icon), null) },
        supportingContent = supporting?.let { { Text(it) } },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = tint,
                leadingIconColor = tint,
                supportingColor = supportingColor,
            ),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(item.label)) }
}

/** The dialog for what was tapped: a new role, a reason to remove them, banning, deleting. */
@Composable
private fun ModerationDialog(
    open: Moderation?,
    moderation: RoomModeration,
    onDone: () -> Unit,
) {
    val item = open ?: return
    val levels = moderation.standing.levels
    when (item) {
        Moderation.Approve, Moderation.Invite, Moderation.Unban -> {
            Unit
        }

        Moderation.Role -> {
            RoleDialog(
                current = levels.of(moderation.userId),
                mine = levels.of(moderation.me),
                usersDefault = levels.usersDefault,
                isSelf = false,
                onChoose = moderation.onSetLevel,
                onDismiss = onDone,
                peerName = moderation.name,
            )
        }

        Moderation.Deny, Moderation.Revoke, Moderation.Kick -> {
            ReasonDialog(
                title = "${stringResource(item.label)}: ${moderation.name}",
                onConfirm = { reason -> item.action?.let { moderation.onMembership(it, reason) } },
                onDismiss = onDone,
            )
        }

        Moderation.Ban -> {
            BanDialog(moderation, canDelete = levels.canRedact(moderation.me, moderation.userId), onDismiss = onDone)
        }

        Moderation.DeleteRecent -> {
            DeleteRecentDialog(moderation, onDismiss = onDone)
        }
    }
}

/** Banning, with a reason, and (when we may) deleting what they sent recently too. */
@Composable
private fun BanDialog(
    moderation: RoomModeration,
    canDelete: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val reason = rememberTextFieldState()
    var delete by rememberSaveable { mutableStateOf(false) }
    var keepState by rememberSaveable { mutableStateOf(true) }
    val count = moderation.recent?.count { !keepState || !it.state }
    val deleting = count?.let { pluralStringResource(R.plurals.moderation_deleting, it, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_gavel), null) },
        title = { Text(stringResource(R.string.moderation_ban_title, moderation.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.moderation_ban_explain))
                ReasonField(reason)
                if (canDelete) {
                    SwitchRow(stringResource(R.string.moderation_ban_delete), null, delete) {
                        delete = it
                        if (it) moderation.onFindRecent()
                    }
                    if (delete) {
                        KeepSystemRow(keepState) { keepState = it }
                        RecentCount(count)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text =
                    reason.text
                        .toString()
                        .trim()
                        .ifEmpty { null }
                moderation.onBan(text, delete, keepState)
                if (delete && (count ?: 0) > 0) deleting?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                onDismiss()
            }) { Text(stringResource(R.string.member_ban), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Deleting what they sent in the room's recent history, system messages kept or not, with a reason. */
@Composable
private fun DeleteRecentDialog(
    moderation: RoomModeration,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val reason = rememberTextFieldState()
    var keepState by rememberSaveable { mutableStateOf(true) }
    val find by rememberUpdatedState(moderation.onFindRecent)
    LaunchedEffect(Unit) { find() }
    val count = moderation.recent?.count { !keepState || !it.state }
    val deleting = count?.let { pluralStringResource(R.plurals.moderation_deleting, it, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete), null) },
        title = { Text(stringResource(R.string.moderation_delete_recent_title, moderation.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecentCount(count)
                ReasonField(reason)
                KeepSystemRow(keepState) { keepState = it }
            }
        },
        confirmButton = {
            TextButton(enabled = count != null && count > 0, onClick = {
                moderation.onDeleteRecent(
                    keepState,
                    reason.text
                        .toString()
                        .trim()
                        .ifEmpty { null }
                )
                deleting?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                onDismiss()
            }) { Text(stringResource(R.string.moderation_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** How many events would go; a spinner while they're looked for. */
@Composable
private fun RecentCount(count: Int?) {
    when (count) {
        null -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.moderation_looking), style = MaterialTheme.typography.bodyMedium)
            }
        }

        0 -> {
            Text(stringResource(R.string.moderation_delete_none), style = MaterialTheme.typography.bodyMedium)
        }

        else -> {
            Text(
                pluralStringResource(R.plurals.moderation_delete_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReasonField(state: TextFieldState) {
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.member_reason)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeepSystemRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = SwitchRow(
    stringResource(R.string.moderation_keep_system),
    stringResource(R.string.moderation_keep_system_summary),
    checked,
    onChange,
)

@Composable
private fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
