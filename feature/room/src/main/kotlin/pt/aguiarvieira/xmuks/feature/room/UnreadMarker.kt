package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * Where reading stopped when the room was opened: our read marker ([eventId]), how many messages
 * came after it, and the marker's time once its event is loaded (null until then).
 */
@Immutable
data class UnreadMarker(
    val eventId: String,
    val count: Int,
    val timestamp: Long? = null,
)

/**
 * [this] (newest first) with "New messages" between the read marker and what followed it. Placed
 * by time, so it lands right even when the marker is an event the timeline doesn't show (an edit,
 * a reaction). Nothing when the marker is older than everything loaded, or nothing came after.
 */
internal fun List<TimelineItem>.withUnreadSeparator(markerTimestamp: Long?): List<TimelineItem> {
    markerTimestamp ?: return this
    val firstRead = indexOfFirst { item -> item.time?.let { it <= markerTimestamp } == true }
    if (firstRead <= 0) return this
    return take(firstRead) + TimelineItem.UnreadSeparator + drop(firstRead)
}

private val TimelineItem.time: Long?
    get() =
        when (this) {
            is TimelineItem.Message -> timestamp
            is TimelineItem.StateChange -> timestamp
            else -> null
        }

/** The "New messages" line between what was read and what wasn't. */
@Composable
internal fun UnreadRow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = color)
        Text(
            stringResource(R.string.new_messages),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        HorizontalDivider(Modifier.weight(1f), color = color)
    }
}

/**
 * "↑ N new messages" at the top of the timeline while the start of what's unread is above the
 * screen (or not loaded yet). [onJump] gets the divider's index when it's loaded, null when not.
 * Once the divider has been on screen the chip is done.
 */
@Composable
internal fun BoxScope.UnreadJump(
    items: List<TimelineItem>,
    list: LazyListState,
    unread: UnreadMarker?,
    onJump: (dividerIndex: Int?) -> Unit,
) {
    unread ?: return
    var seen by remember(unread.eventId) { mutableStateOf(false) }
    val divider = items.indexOf(TimelineItem.UnreadSeparator)
    val above by remember(divider) {
        derivedStateOf {
            divider < 0 || (
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            ) < divider
        }
    }
    LaunchedEffect(above) { if (!above) seen = true }
    if (seen || !above) return
    ExtendedFloatingActionButton(
        onClick = { onJump(divider.takeIf { it >= 0 }) },
        icon = { Icon(painterResource(R.drawable.ic_arrow_up), null) },
        text = { Text(pluralStringResource(R.plurals.unread_jump, unread.count, unread.count)) },
        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
    )
}
