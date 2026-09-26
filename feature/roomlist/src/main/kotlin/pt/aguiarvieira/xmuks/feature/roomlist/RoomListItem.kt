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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.Preview
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.designsystem.component.AvatarKind
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps

@Composable
fun RoomListItem(
    room: RoomSummary,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
) {
    val unread = room.unread.any
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomAvatar(
            name = room.name,
            id = room.roomId,
            avatarUrl = room.avatarUrl,
            kind = if (room.isDirect) AvatarKind.Person else AvatarKind.Room,
            modifier = Modifier.sharedElement(SharedKeys.avatar(room.roomId)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).sharedElement(SharedKeys.title(room.roomId)),
                )
                Text(
                    text = ListTimestamps.format(room.timestamp, now, is24Hour = is24Hour),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = previewLine(room),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (level, count) = room.unread.level()
                UnreadBadge(level, count)
            }
        }
    }
}

@Composable
private fun previewLine(room: RoomSummary): String {
    val body =
        when (val p = room.preview) {
            is Preview.Text -> p.text
            Preview.Sticker -> stringResource(R.string.preview_sticker)
            Preview.Encrypted -> stringResource(R.string.preview_encrypted)
            Preview.None -> return ""
        }
    val sender =
        when {
            room.previewFromMe -> stringResource(R.string.preview_you)

            room.isDirect -> null

            // in a DM the other person is implied
            else -> room.previewSender
        }
    return if (sender == null) body else stringResource(R.string.preview_sender, sender, body)
}

/** Loudest first: mentions, then notifying messages, then quiet unread (or marked unread). */
fun Unread.level(): Pair<UnreadLevel, Int> =
    when {
        highlights > 0 -> UnreadLevel.Mention to highlights
        notifications > 0 -> UnreadLevel.Count to notifications
        any -> UnreadLevel.Dot to 0
        else -> UnreadLevel.None to 0
    }
