package pt.aguiarvieira.xmuks.feature.room

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** Once a context window is in, scroll to its event and light it up; if it failed, say so and go back. */
@Composable
internal fun JumpEffects(
    context: ContextView?,
    list: LazyListState,
    onLeaveContext: () -> Unit,
    highlight: (String) -> Unit,
) {
    val androidContext = LocalContext.current
    val failedText = stringResource(R.string.context_failed)
    val leave by rememberUpdatedState(onLeaveContext)
    val light by rememberUpdatedState(highlight)
    LaunchedEffect(context?.eventId, context?.items != null, context?.failed) {
        val view = context ?: return@LaunchedEffect
        if (view.failed) {
            Toast.makeText(androidContext, failedText, Toast.LENGTH_SHORT).show()
            leave()
            return@LaunchedEffect
        }
        val index = view.items?.indexOfEvent(view.eventId) ?: return@LaunchedEffect
        if (index >= 0) {
            list.scrollToItem(index, list.focusOffset())
            light(view.eventId)
        }
    }
}

/**
 * The room was opened at an event that's in the live timeline: once it's laid out, scroll to it and
 * light it up. Not shown after all (a hidden event, or the list never got it): a window around it.
 */
@Composable
internal fun FocusEffect(
    focus: String?,
    shown: () -> List<TimelineItem>?,
    list: LazyListState,
    onShowContext: (String) -> Unit,
    onFocusConsume: () -> Unit,
    highlight: (String) -> Unit,
) {
    val items by rememberUpdatedState(shown)
    val showContext by rememberUpdatedState(onShowContext)
    val done by rememberUpdatedState(onFocusConsume)
    val light by rememberUpdatedState(highlight)
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val index =
            withTimeoutOrNull(FOCUS_WAIT_MS) {
                snapshotFlow { list.layoutInfo.viewportSize.height > 0 }.first { it }
                snapshotFlow { items()?.indexOfEvent(target) ?: -1 }.first { it >= 0 }
            }
        if (index != null) {
            list.scrollToItem(index, list.focusOffset())
            light(target)
        } else {
            showContext(target)
        }
        done()
    }
}

/**
 * Where a jumped-to item should sit: a third of the way up rather than on the bottom edge (in this
 * reversed list, "scroll to item" puts it at the start, i.e. the bottom). Negative = further up.
 */
internal fun LazyListState.focusOffset() = -(layoutInfo.viewportSize.height / FOCUS_FRACTION)

internal val TimelineItem.eventId: String?
    get() =
        when (this) {
            is TimelineItem.Message -> eventId
            is TimelineItem.StateChange -> eventId
            is TimelineItem.Hidden -> eventId
            is TimelineItem.Call -> eventId
            is TimelineItem.DaySeparator -> null
            TimelineItem.UnreadSeparator -> null
        }

internal fun List<TimelineItem>.indexOfEvent(eventId: String) = indexOfFirst { it.eventId == eventId }

private const val FOCUS_WAIT_MS = 2_000L
private const val FOCUS_FRACTION = 3
