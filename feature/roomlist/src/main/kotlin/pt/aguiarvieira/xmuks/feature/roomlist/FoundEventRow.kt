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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvent
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/** A notification or search hit: the room, who said what, and when; opening the room at it. */
@Composable
internal fun FoundEventRow(
    found: FoundEvent,
    avatar: (String?) -> String?,
    time: String,
    onClick: () -> Unit,
) {
    val roomName = found.room?.name ?: found.roomId
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(roomName, found.roomId, found.room?.avatarUrl, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    roomName,
                    style = MaterialTheme.typography.titleSmall,
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
            // In a DM the room is the sender already.
            if (found.room?.isDirect != true) {
                SenderLine(found, avatar)
            }
            Text(
                found.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
