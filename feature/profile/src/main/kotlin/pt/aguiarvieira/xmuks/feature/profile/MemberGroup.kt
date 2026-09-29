package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomMember
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard

/** One heading of the member list and who's under it. */
internal data class MemberGroup(
    val key: String,
    val kind: Kind,
    /** For joined members: their shared level ([PowerLevels.CREATOR] for creators). */
    val level: Long?,
    val members: List<RoomMember>,
) {
    enum class Kind { Knocking, Joined, Invited }
}

/**
 * The member list's groups, [query] applied: people asking to join first (they're waiting on
 * someone), then the joined by power level, highest first — creators above all — then the
 * invited. Nobody who's gone (left, kicked or banned) is listed.
 */
internal fun groupMembers(
    info: RoomInfo,
    query: String,
): List<MemberGroup> {
    val q = query.trim().lowercase()
    val matching = info.members.filter { q.isEmpty() || q in it.name.lowercase() || q in it.userId.lowercase() }
    val byMembership = matching.groupBy { it.membership }

    fun group(
        membership: Membership,
        kind: MemberGroup.Kind,
    ) = byMembership[membership]
        ?.takeIf { it.isNotEmpty() }
        ?.let { MemberGroup(kind.name, kind, null, it) }

    val joined =
        byMembership[Membership.Join]
            .orEmpty()
            .groupBy { it.powerLevel }
            .toSortedMap(compareByDescending { it })
            .map { (level, members) -> MemberGroup("level-$level", MemberGroup.Kind.Joined, level, members) }
    return listOfNotNull(group(Membership.Knock, MemberGroup.Kind.Knocking)) +
        joined +
        listOfNotNull(group(Membership.Invite, MemberGroup.Kind.Invited))
}

/** A group's heading: its role (or level), and how many are in it. */
@Composable
internal fun groupTitle(
    group: MemberGroup,
    usersDefault: Long,
): String {
    val name =
        when (group.kind) {
            MemberGroup.Kind.Knocking -> stringResource(R.string.members_knocking)
            MemberGroup.Kind.Invited -> stringResource(R.string.members_invited)
            MemberGroup.Kind.Joined -> levelGroupName(group.level ?: usersDefault, usersDefault)
        }
    return "$name · ${group.members.size}"
}

@Composable
private fun levelGroupName(
    level: Long,
    usersDefault: Long,
): String =
    when (level) {
        PowerLevels.CREATOR -> stringResource(R.string.group_creators)
        PowerLevels.ADMIN -> stringResource(R.string.group_admins)
        PowerLevels.MODERATOR -> stringResource(R.string.group_moderators)
        usersDefault -> stringResource(R.string.members)
        else -> stringResource(R.string.group_level, level)
    }

/** On the room info screen: how many are in the room, and the way to the full list. */
@Composable
internal fun MembersCard(
    info: RoomInfo,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val joined = info.members(Membership.Join).size
    val knocking = info.members(Membership.Knock).size
    val invited = info.members(Membership.Invite).size
    val details =
        listOfNotNull(
            pluralStringResource(R.plurals.members_joined, joined, joined),
            pluralStringResource(R.plurals.members_asking, knocking, knocking).takeIf { knocking > 0 },
            pluralStringResource(R.plurals.members_invited_count, invited, invited).takeIf { invited > 0 },
        ).joinToString(" · ")
    ScreenCard(modifier) {
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_person), null) },
            supportingContent = { Text(details) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onOpen),
        ) { Text(stringResource(R.string.members)) }
    }
}

@Composable
internal fun MemberRow(
    member: RoomMember,
    info: RoomInfo,
    me: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val usersDefault = info.powerLevels.usersDefault
    ListItem(
        leadingContent = { RoomAvatar(member.name, member.userId, media.thumbnail(member.avatarMxc), size = 40.dp) },
        supportingContent = {
            Text(
                member.reason ?: member.userId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent =
            if (member.powerLevel != usersDefault) {
                { Text(roleName(member.powerLevel, usersDefault), style = MaterialTheme.typography.labelMedium) }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable { open = true },
    ) { Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (open) MemberSheet(member, info, me, media, actions) { open = false }
}
