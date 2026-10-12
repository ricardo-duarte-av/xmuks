package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineSnapshot

/**
 * Shows an event asked for from elsewhere (a mention, a search hit, a link): in the live timeline
 * when it's loaded or a few pages back (gomuks pages from its own store first), else in a window
 * around it ([showContext], `get_event_context`).
 */
class EventJump(
    private val scope: CoroutineScope,
    private val timeline: Flow<TimelineSnapshot>,
    private val loadOlder: suspend () -> Unit,
    private val showContext: (eventId: String) -> Unit,
) {
    private val _focus = MutableStateFlow<String?>(null)

    /** The event to land on, found in the live timeline: the screen scrolls to it, then calls [done]. */
    val focus: StateFlow<String?> = _focus.asStateFlow()

    /** The last event asked for: coming back to the screen doesn't jump again. */
    private var reached: String? = null

    fun reach(eventId: String) {
        if (eventId == reached) return
        reached = eventId
        scope.launch {
            val live = withTimeoutOrNull(TIMEOUT_MS) { inLiveTimeline(eventId) } == true
            if (live) _focus.value = eventId else showContext(eventId)
        }
    }

    fun done() {
        _focus.value = null
    }

    private suspend fun inLiveTimeline(eventId: String): Boolean {
        var pages = 0
        while (true) {
            val snapshot = timeline.first { it.loaded && !it.loadingOlder }
            if (snapshot.events.any { it.eventId == eventId }) return true
            if (!snapshot.hasMoreBefore || pages++ == PAGES) return false
            loadOlder()
        }
    }

    private companion object {
        /** How far back the live timeline is paged before a window around the event is shown instead. */
        const val PAGES = 3
        const val TIMEOUT_MS = 10_000L
    }
}
