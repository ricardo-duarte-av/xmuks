package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.NetworkBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * "profile via sender" with each name in its own colour (gomuks colours the profile by the
 * profile's ID, the sender by theirs); a plain sender name otherwise.
 */
@Composable
internal fun SenderName(
    label: SenderLabel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(senderText(label), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The label as styled text, for running into other text (emotes). */
@Composable
internal fun senderText(label: SenderLabel): AnnotatedString {
    val senderColor = senderColor(label.senderId)
    val profileColor = label.profileId?.let { senderColor(it) } ?: senderColor
    val via = MaterialTheme.colorScheme.onSurfaceVariant
    val viaWord = stringResource(R.string.via)
    return remember(label, senderColor, profileColor, via, viaWord) {
        buildAnnotatedString {
            val profile = label.profileName
            if (profile != null) {
                withStyle(SpanStyle(color = profileColor)) { append(profile) }
                withStyle(SpanStyle(color = via, fontWeight = FontWeight.Normal)) { append(" $viaWord ") }
            }
            withStyle(SpanStyle(color = senderColor)) { append(label.senderName) }
        }
    }
}

/** Avatar and name, the name centred on the avatar. */
@Composable
internal fun Header(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    actions: TimelineActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Box {
            RoomAvatar(
                message.label.shownName,
                message.label.profileId ?: message.sender,
                resolver.avatar(message.senderAvatarMxc),
                size = AVATAR_SIZE,
                modifier =
                    Modifier.clip(CircleShape).clickable {
                        resolver.image(message.senderAvatarMxc, message.senderName)?.let(actions.openMedia)
                    },
            )
            // A bridged user: their network's logo, bottom right, as on a bridged room's avatar.
            message.senderNetwork?.let { network ->
                val label = stringResource(R.string.sender_network, network.name)
                NetworkBadge(
                    network.name,
                    resolver.avatar(network.logoMxc),
                    size = NETWORK_BADGE_SIZE,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = NETWORK_BADGE_OFFSET, y = NETWORK_BADGE_OFFSET)
                            .semantics { contentDescription = label },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        SenderName(
            message.label,
            MaterialTheme.typography.labelLarge,
            Modifier.clickable { actions.openUser(message.sender) },
        )
    }
}

/** On our own messages sent as a per-message profile: its name and avatar, at the right. */
@Composable
internal fun OwnProfileHeader(
    message: TimelineItem.Message,
    resolver: MediaResolver,
) {
    val label = message.label
    val name = label.profileName ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = senderColor(label.profileId ?: message.sender),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        RoomAvatar(
            name,
            label.profileId ?: message.sender,
            resolver.avatar(message.senderAvatarMxc),
            size = AVATAR_SIZE
        )
    }
}

private val AVATAR_SIZE = 28.dp
private val NETWORK_BADGE_SIZE = 14.dp
private val NETWORK_BADGE_OFFSET = 3.dp
