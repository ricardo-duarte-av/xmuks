package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.Invite
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps

/** "Invites": above a tab's rooms while any wait for an answer. */
@Composable
internal fun InvitesHeading(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.invites),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** One invite: what it is and who sent it; tapping it opens the invite to answer. */
@Composable
internal fun InviteListItem(
    row: InviteRow,
    now: Long,
    is24Hour: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val invite = row.invite
    val display = LocalRoomListDisplay.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = display.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomAvatar(name = invite.name, id = invite.roomId, avatarUrl = row.avatarUrl, size = display.avatar)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = invite.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (invite.createdAt > 0) {
                    Text(
                        text = ListTimestamps.format(invite.createdAt, now, is24Hour = is24Hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = inviteLine(invite),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Badge { Text(stringResource(R.string.invite_badge)) }
            }
        }
    }
}

/** "Direct message from Toby_A", "Toby_A invited you to a room", or to a space. */
@Composable
private fun inviteLine(invite: Invite): String {
    val inviter = invite.inviterName ?: invite.inviterId
    return when {
        inviter == null -> stringResource(R.string.invite_line_unknown)
        invite.isSpace -> stringResource(R.string.invite_line_space, inviter)
        invite.isDirect -> stringResource(R.string.invite_line_dm, inviter)
        else -> stringResource(R.string.invite_line_room, inviter)
    }
}
