package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomMember
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** Something done to a member, asked about first (with a reason) when it's a removal. */
private enum class MemberAction(
    val label: Int,
    val action: MembershipAction?,
) {
    Profile(R.string.member_profile, null),
    Role(R.string.member_role, null),
    Approve(R.string.member_approve, MembershipAction.Invite),
    Deny(R.string.member_deny, MembershipAction.Kick),
    Revoke(R.string.member_revoke, MembershipAction.Kick),
    Kick(R.string.member_kick, MembershipAction.Kick),
    Ban(R.string.member_ban, MembershipAction.Ban),
    Unban(R.string.member_unban, MembershipAction.Unban),
}

/** What we may do about [member], given our power and their membership. */
private fun actionsFor(
    member: RoomMember,
    info: RoomInfo,
    me: String,
): List<MemberAction> {
    val levels = info.powerLevels
    val target = member.userId
    val kick = levels.canKick(me, target)
    val ban = levels.canBan(me, target)
    return buildList {
        add(MemberAction.Profile)
        if (member.membership == Membership.Join && levels.canChangeLevel(me, target)) add(MemberAction.Role)
        addAll(membershipActions(member.membership, levels.canInvite(me), kick, ban))
        if (member.membership != Membership.Ban && ban) add(MemberAction.Ban)
    }
}

/** What undoes or settles each membership: letting knockers in, revoking invites, removing, unbanning. */
private fun membershipActions(
    membership: Membership,
    invite: Boolean,
    kick: Boolean,
    ban: Boolean,
): List<MemberAction> =
    when (membership) {
        Membership.Knock -> listOfNotNull(MemberAction.Approve.takeIf { invite }, MemberAction.Deny.takeIf { kick })
        Membership.Invite -> listOfNotNull(MemberAction.Revoke.takeIf { kick })
        Membership.Join -> listOfNotNull(MemberAction.Kick.takeIf { kick })
        Membership.Ban -> listOfNotNull(MemberAction.Unban.takeIf { ban })
        Membership.Leave -> emptyList()
    }

/** A member: who they are and their role, and what we can do about them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberSheet(
    member: RoomMember,
    info: RoomInfo,
    me: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
    onDismiss: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf<MemberAction?>(null) }
    val usersDefault = info.powerLevels.usersDefault
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                leadingContent = {
                    RoomAvatar(
                        member.name,
                        member.userId,
                        media.thumbnail(member.avatarMxc),
                        size = 56.dp
                    )
                },
                supportingContent = {
                    Text("${member.userId} · ${roleName(member.powerLevel, usersDefault)}")
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(member.name, style = MaterialTheme.typography.titleMedium) }
            actionsFor(member, info, me).forEach { action ->
                val destructive = action.action == MembershipAction.Kick || action.action == MembershipAction.Ban
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier =
                        Modifier.clickable {
                            when (action) {
                                MemberAction.Profile -> {
                                    onDismiss()
                                    actions.openUser(member.userId)
                                }

                                MemberAction.Approve, MemberAction.Unban -> {
                                    action.action?.let { actions.membership(member.userId, it, null) }
                                    onDismiss()
                                }

                                else -> {
                                    confirming = action
                                }
                            }
                        },
                ) {
                    Text(
                        stringResource(action.label),
                        color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        }
    }
    MemberConfirm(confirming, member, info, me, actions) {
        confirming = null
        onDismiss()
    }
}

@Composable
private fun MemberConfirm(
    action: MemberAction?,
    member: RoomMember,
    info: RoomInfo,
    me: String,
    actions: RoomInfoActions,
    onDone: () -> Unit,
) {
    if (action == null) return
    when {
        action == MemberAction.Role -> {
            RoleDialog(
                current = member.powerLevel,
                mine = info.powerLevels.of(me),
                usersDefault = info.powerLevels.usersDefault,
                isSelf = member.userId == me,
                onChoose = { actions.setLevel(member.userId, it) },
                onDismiss = onDone,
            )
        }

        action.action != null -> {
            ReasonDialog(
                title = "${stringResource(action.label)}: ${member.name}",
                onConfirm = { reason -> actions.membership(member.userId, action.action, reason) },
                onDismiss = onDone,
            )
        }
    }
}
