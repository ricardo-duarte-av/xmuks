package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * A call in the timeline: one card per call, however many joins and leaves it took. While it's
 * still going (and the room says so) it offers to join.
 */
@Composable
internal fun CallRow(
    item: TimelineItem.Call,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val ongoing = item.endedAt == null && actions.callOngoing
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().highlight(highlighted).padding(horizontal = 16.dp, vertical = 6.dp), Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (ongoing) colors.primaryContainer else colors.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (item.video) R.drawable.ic_videocam else R.drawable.ic_call),
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        stringResource(
                            if (item.video) R.string.call_card_video else R.string.call_card_voice,
                            item.starterName,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        callDetails(item, ongoing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val join = actions.joinCall
                if (ongoing && join != null) {
                    FilledTonalButton(onClick = { join(item.video) }) { Text(stringResource(R.string.call_card_join)) }
                }
            }
        }
    }
}

@Composable
private fun callDetails(
    item: TimelineItem.Call,
    ongoing: Boolean,
): String {
    val people = pluralStringResource(R.plurals.call_people, item.participants, item.participants)
    val time = rememberTime(item.startedAt)
    val ended = item.endedAt
    return when {
        ongoing -> stringResource(R.string.call_card_ongoing, time, people)
        ended != null -> stringResource(R.string.call_card_ended, time, minutes(ended - item.startedAt), people)
        else -> stringResource(R.string.call_card_started, time, people)
    }
}

private fun minutes(ms: Long): String {
    val m = (ms / MS_PER_MINUTE).coerceAtLeast(0)
    return when {
        m == 0L -> "${(ms / MS_PER_SECOND).coerceAtLeast(0)}s"
        m < MINUTES_PER_HOUR -> "${m}m"
        else -> "${m / MINUTES_PER_HOUR}h ${m % MINUTES_PER_HOUR}m"
    }
}

private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
