package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvent
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * A notification or search hit: the room, who said what, and when; opening the room at it. Under a
 * [FoundRoomHeader] (one room's hits) the room goes without saying, so the sender leads instead.
 */
@Composable
internal fun FoundEventRow(
    found: FoundEvent,
    avatar: (String?) -> String?,
    time: String,
    showRoom: Boolean = true,
    onClick: () -> Unit,
) {
    val roomName = found.room?.name ?: found.roomId
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showRoom) {
            RoomAvatar(roomName, found.roomId, found.room?.avatarUrl, size = 40.dp)
        } else {
            RoomAvatar(found.senderName, found.sender, avatar(found.senderAvatarMxc), size = 40.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showRoom) roomName else found.senderName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (showRoom) Color.Unspecified else senderColor(found.sender),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showRoom && !senderIsTheRoom(found)) SenderLine(found, avatar)
            Text(
                found.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * In a DM the room is the other person, so their messages need no sender line; ours (or a bridge
 * bot's) do. When gomuks hasn't said who the other person is, every DM message is taken as theirs.
 */
private fun senderIsTheRoom(found: FoundEvent): Boolean {
    val room = found.room ?: return false
    if (!room.isDirect) return false
    return room.dmUserId == null || room.dmUserId == found.sender
}

/** Above one room's hits: which room they're all in. */
@Composable
internal fun FoundRoomHeader(
    room: RoomSummary?,
    roomId: String,
) {
    val name = room?.name ?: roomId
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomAvatar(name, roomId, room?.avatarUrl, size = 24.dp)
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SenderLine(
    found: FoundEvent,
    avatar: (String?) -> String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoomAvatar(found.senderName, found.sender, avatar(found.senderAvatarMxc), size = 18.dp)
        Text(
            found.senderName,
            style = MaterialTheme.typography.labelLarge,
            color = senderColor(found.sender),
            maxLines = 1,
        )
    }
}
