package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The day between two days' messages: a pill in the middle of the row. */
@Composable
internal fun DayRow(
    day: LocalDate,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { DayPill(day) }
}

/**
 * The day of the topmost message on screen, sliding down from the top while the list is pulled
 * back into history, and away again a few seconds after it comes to rest.
 */
@Composable
internal fun FloatingDay(
    items: List<TimelineItem>,
    list: LazyListState,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(items)
    // Newest first: a day's separator sits above (after) its messages, so the topmost item's day
    // is the first separator from it on.
    val day by remember(list) {
        derivedStateOf {
            val top =
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: return@derivedStateOf null
            current
                .asSequence()
                .drop(top)
                .filterIsInstance<TimelineItem.DaySeparator>()
                .firstOrNull()
                ?.day
        }
    }
    // The last day there was, so the pill keeps its text while it slides away.
    var last by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(day) { day?.let { last = it } }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.lastScrolledForward }.collectLatest { (scrolling, older) ->
            // Forward is up the list (older), toward the oldest loaded message.
            if (scrolling) {
                if (older) shown = true
            } else {
                delay(FLOATING_DAY_MS)
                shown = false
            }
        }
    }
    AnimatedVisibility(
        shown && last != null,
        modifier = modifier.padding(top = 8.dp),
        enter = slideInVertically { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
    ) {
        last?.let { DayPill(it) }
    }
}

@Composable
private fun DayPill(day: LocalDate) {
    val text = remember(day) { day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private const val FLOATING_DAY_MS = 2_500L
