package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * An event with nothing to show but that it happened: who sent it and its type, the way gomuks
 * web shows hidden events (`{ "type": "m.reaction" }`), small and out of the way.
 */
@Composable
internal fun HiddenRow(
    item: TimelineItem.Hidden,
    resolver: MediaResolver,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // It's an event like any other: held, it offers what a message does (react, reply, delete).
    val message = item.asMessage()
    Column(
        // At the left edge, where others' messages start.
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {},
                onLongClick = { actions.onMessageMenu(message) },
            ).padding(start = EDGE, end = 16.dp, top = ROW_GAP),
    ) {
        Header(item, resolver, actions, muted)
        if (item.reactions.isNotEmpty()) {
            Reactions(
                item.reactions,
                resolver,
                Modifier.padding(start = AVATAR_SIZE + 4.dp, top = 4.dp),
                onToggle = { key -> actions.onReaction(message, key) },
                onHold = { key -> actions.showReactions(item.eventId, key) },
            )
        }
    }
}

/** Who sent it, its type and when, on one line. */
@Composable
private fun Header(
    item: TimelineItem.Hidden,
    resolver: MediaResolver,
    actions: TimelineActions,
    muted: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // As in a message's header: the avatar opens the picture, the name the person.
            RoomAvatar(
                item.senderName,
                item.sender,
                resolver.avatar(item.senderAvatarMxc),
                size = AVATAR_SIZE,
                modifier =
                    Modifier.clip(CircleShape).clickable {
                        resolver.image(item.senderAvatarMxc, item.senderName)?.let(actions.openMedia)
                    },
            )
            Text(
                item.senderName,
                color = senderColor(item.sender),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { actions.openUser(item.sender) },
            )
        }
        Text(
            "{ \"type\": \"${item.type}\" }",
            color = muted,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            // The type is the point: it gets the room, the name what's left.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(rememberTime(item.timestamp), color = muted, style = MaterialTheme.typography.labelSmall)
    }
}

/** The event as the message menu and the composer take it: content it has none of, only its type. */
internal fun TimelineItem.Hidden.asMessage() =
    TimelineItem.Message(
        key = key,
        eventId = eventId,
        sender = sender,
        label = SenderLabel(sender, senderName),
        senderAvatarMxc = senderAvatarMxc,
        fromMe = fromMe,
        timestamp = timestamp,
        content = MessageContent.Unsupported(type, null),
        reply = null,
        reactions = reactions,
        edited = false,
        firstInGroup = true,
        lastInGroup = true,
        readBy = emptyList(),
        sendError = null,
    )

private val AVATAR_SIZE = 16.dp
private val EDGE = 12.dp

/**
 * Above each row only, as messages space themselves (their gap is at their top): so a hidden event
 * sits 8dp from what's above it and from a message below it, and two of them 8dp apart.
 */
private val ROW_GAP = 8.dp
