package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
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

/** Moves the content up by [by], taking the same height out of the layout. */
internal fun Modifier.raise(by: TextUnit): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = by.roundToPx().coerceAtMost(placeable.height)
        layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
    }

/** Google Sans Flex's space above its ascenders, as a fraction of the font size (measured). */
internal const val ASCENT_GAP = 0.23f

/** Avatar and name, their tops on the same line. */
@Composable
internal fun Header(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    onOpenMedia: (ViewerMedia) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 4.dp)) {
        RoomAvatar(
            message.label.shownName,
            message.label.profileId ?: message.sender,
            resolver.avatar(message.senderAvatarMxc),
            size = AVATAR_SIZE,
            modifier =
                Modifier.clip(CircleShape).clickable {
                    resolver.image(message.senderAvatarMxc, message.senderName)?.let(onOpenMedia)
                },
        )
        Spacer(Modifier.width(8.dp))
        // No leading above the line, and the font's own room above its ascenders taken back.
        val nameStyle =
            MaterialTheme.typography.labelLarge.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.FirstLineTop),
            )
        SenderName(message.label, nameStyle, Modifier.raise(nameStyle.fontSize * ASCENT_GAP))
    }
}

private val AVATAR_SIZE = 28.dp
