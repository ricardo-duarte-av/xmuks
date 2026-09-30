package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * A list that stays at its top while it's there: opened (or at rest at the top), a room moving to
 * the top shows up there instead of the list following whatever was first down out of view.
 * Scrolled away by hand, the list stays where it was put; back at the top, it follows again.
 */
@Composable
internal fun rememberTopAnchoredState(items: Any?): LazyListState {
    val state = rememberLazyListState()
    val anchor = remember { TopAnchor() }
    LaunchedEffect(state) {
        snapshotFlow {
            (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) to
                state.isScrollInProgress
        }.collect { (top, scrolling) ->
            when {
                top -> anchor.following = true
                scrolling -> anchor.following = false
            }
        }
    }
    // During composition, so the list is measured at its top in the same frame the new order lands.
    remember(items) { if (anchor.following) state.requestScrollToItem(0) }
    return state
}

/** Not state: reading it must not recompose, only the list's changes decide. */
private class TopAnchor {
    var following = true
}
