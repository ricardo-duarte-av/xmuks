package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.CallBadge
import pt.aguiarvieira.xmuks.core.data.rooms.Preview
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.designsystem.component.NetworkBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps
import pt.aguiarvieira.xmuks.core.richtext.rememberHtmlSnippet
import pt.aguiarvieira.xmuks.core.richtext.rememberPills

@Composable
fun RoomListItem(
    room: RoomSummary,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    sharedScope: String = SharedScopes.CHATS,
    /** A long press: the room's menu. */
    onLongClick: (() -> Unit)? = null,
) {
    val unread = room.unread.any
    val colors = MaterialTheme.colorScheme
    val display = LocalRoomListDisplay.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = display.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            RoomAvatar(
                name = room.name,
                id = room.roomId,
                avatarUrl = room.avatarUrl,
                size = display.avatar,
                modifier = Modifier.sharedElement(SharedKeys.avatar(room.roomId, sharedScope)),
            )
            // A call going on: a badge top right, where it can't be mistaken for the bridge's.
            room.call?.let { call ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
                        .size(display.avatar * BADGE_FRACTION)
                        .background(colors.primary, CircleShape)
                        .border(2.dp, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (call == CallBadge.Video) R.drawable.ic_videocam else R.drawable.ic_call),
                        contentDescription = stringResource(R.string.call_in_progress),
                        tint = colors.onPrimary,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                    )
                }
            }
            // Bridged elsewhere: that network's logo, bottom right.
            room.bridgeProtocol?.let { protocol ->
                NetworkBadge(
                    protocol,
                    room.bridgeAvatarUrl,
                    size = display.avatar * BADGE_FRACTION,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = BADGE_OFFSET, y = BADGE_OFFSET)
                            .sharedElement(SharedKeys.bridge(room.roomId, sharedScope)),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).sharedElement(SharedKeys.title(room.roomId, sharedScope)),
                )
                Text(
                    text = ListTimestamps.format(room.timestamp, now, is24Hour = is24Hour),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val line = if (display.showPreview) previewLine(room) else AnnotatedString("")
                // Mentions in the preview are drawn as the timeline draws them.
                val pills = rememberPills(line)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { pills.layout = it },
                    modifier = Modifier.weight(1f).then(pills.modifier),
                )
                room.call?.let { CallPulse(it) }
                val (level, count) = room.unread.level(display.unreadCount)
                UnreadBadge(level, count, mentionAt = display.mentionAt)
            }
        }
    }
}

/** A call going on: its icon, breathing, beside the unread badge (the preview stays the room's last message). */
@Composable
private fun CallPulse(call: CallBadge) {
    val pulse = rememberInfiniteTransition(label = "call")
    val beat by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )
    Icon(
        painterResource(if (call == CallBadge.Video) R.drawable.ic_videocam else R.drawable.ic_call),
        contentDescription = stringResource(R.string.call_in_progress),
        tint = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.size(18.dp).graphicsLayer {
                val scale = PULSE_MIN_SCALE + (1f - PULSE_MIN_SCALE) * beat
                scaleX = scale
                scaleY = scale
                alpha = PULSE_MIN_ALPHA + (1f - PULSE_MIN_ALPHA) * beat
            },
    )
}

private const val PULSE_MS = 900
private const val PULSE_MIN_SCALE = 0.8f
private const val PULSE_MIN_ALPHA = 0.45f

@Composable
private fun previewLine(room: RoomSummary): AnnotatedString {
    val body =
        when (val p = room.preview) {
            is Preview.Text -> p.html?.let { rememberHtmlSnippet(it) } ?: AnnotatedString(p.text)
            Preview.Sticker -> AnnotatedString(stringResource(R.string.preview_sticker))
            Preview.Encrypted -> AnnotatedString(stringResource(R.string.preview_encrypted))
            Preview.None -> return AnnotatedString("")
        }
    val sender =
        when {
            room.previewFromMe -> stringResource(R.string.preview_you)

            room.isDirect -> null

            // in a DM the other person is implied
            else -> room.previewSender
        }
    if (sender == null) return body
    // The body keeps its styling: the string only places it beside the sender.
    val (before, after) = stringResource(R.string.preview_sender, sender, BODY_MARK).split(BODY_MARK, limit = 2)
    return AnnotatedString(before) + body + AnnotatedString(after)
}

private const val BODY_MARK = "\u0000"

/**
 * One rule for rooms, spaces and tabs: anything needing attention shows a number (red when it
 * includes a mention); quiet unread shows a dot. With [showCount] the number is always the unread
 * messages: a mention only turns the badge red, it doesn't swap the count for its own.
 */
fun Unread.level(showCount: Boolean = false): Pair<UnreadLevel, Int> {
    val attention = maxOf(notifications, highlights)
    val count = if (showCount) maxOf(messages, attention) else attention
    return when {
        highlights > 0 -> UnreadLevel.Mention to count
        count > 0 -> UnreadLevel.Count to count
        any -> UnreadLevel.Dot to 0
        else -> UnreadLevel.None to 0
    }
}

/** Where a room or space was opened from; see [SharedKeys]. */
object SharedScopes {
    const val CHATS = "chats"
    const val DMS = "dms"
    const val SPACES = "spaces"

    fun space(spaceId: String) = "space:$spaceId"
}

/** The network badge: this share of the avatar, peeking out past its corner. */
private const val BADGE_FRACTION = 0.42f
private val BADGE_OFFSET = 3.dp
