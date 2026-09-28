package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
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
    val senderColor = senderColor(label.senderId)
    val profileColor = label.profileId?.let { senderColor(it) } ?: senderColor
    val via = MaterialTheme.colorScheme.onSurfaceVariant
    val viaWord = stringResource(R.string.via)
    val text =
        remember(label, senderColor, profileColor, via, viaWord) {
            buildAnnotatedString {
                val profile = label.profileName
                if (profile != null) {
                    withStyle(SpanStyle(color = profileColor)) { append(profile) }
                    withStyle(SpanStyle(color = via, fontWeight = FontWeight.Normal)) { append(" $viaWord ") }
                }
                withStyle(SpanStyle(color = senderColor)) { append(label.senderName) }
            }
        }
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
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
