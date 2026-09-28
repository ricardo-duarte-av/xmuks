package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Reader
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/** What a timeline row can ask the screen to do. */
@Immutable
class TimelineActions(
    val openMedia: (ViewerMedia) -> Unit,
    /** Show this event: scroll to it if loaded, else load a window around it. */
    val jumpTo: (eventId: String) -> Unit,
)

/** Briefly tints the row a jump landed on, so the eye finds it. */
@Composable
internal fun Modifier.highlight(on: Boolean): Modifier {
    val color by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = HIGHLIGHT_ALPHA) else Color.Transparent,
        animationSpec = tween(HIGHLIGHT_FADE_MS),
        label = "highlight",
    )
    return background(color, RoundedCornerShape(12.dp))
}

/**
 * Who has read up to here: up to three small overlapping avatars, then "+N". Newest reader in
 * front. Receipts move live as sync_complete brings new ones.
 */
@Composable
internal fun ReadReceipts(
    readers: List<Reader>,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val shown = readers.take(MAX_SHOWN)
    val names = readers.joinToString { it.name }
    val description = pluralStringResource(R.plurals.read_by, readers.size, readers.size, names)
    Row(
        modifier = modifier.padding(top = 2.dp).semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RING + STEP * (shown.size - 1))) {
            // Drawn back to front, so the newest reader sits on top at the right.
            shown.asReversed().forEachIndexed { index, reader ->
                Box(
                    Modifier
                        .offset(x = STEP * index)
                        .size(RING)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(1.dp),
                ) {
                    RoomAvatar(reader.name, reader.userId, resolver.avatar(reader.avatarMxc), size = AVATAR)
                }
            }
        }
        if (readers.size > MAX_SHOWN) {
            Text(
                "+${readers.size - MAX_SHOWN}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

private const val MAX_SHOWN = 3
private val AVATAR = 16.dp
private val RING = 18.dp
private val STEP = 11.dp
private const val HIGHLIGHT_ALPHA = 0.55f
private const val HIGHLIGHT_FADE_MS = 600
