package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfile
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** This room profile, if it shows someone other than the global profile does (name or avatar). */
internal fun RoomProfile?.differingFrom(global: UserProfile): RoomProfile? =
    this?.takeIf { it.displayName != global.displayName || it.avatarMxc != global.avatarMxc }

/** Under a room profile: which room it's for, and the global profile, smaller. */
@Composable
internal fun GlobalProfileLine(
    roomName: String,
    globalName: String,
    profile: UserProfile,
    media: ProfileMedia,
) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.profile_in_room, roomName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomAvatar(globalName, profile.userId, media.thumbnail(profile.avatarMxc), size = GLOBAL_AVATAR)
            Column {
                Text(
                    stringResource(R.string.profile_elsewhere),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    globalName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val GLOBAL_AVATAR = 32.dp
