package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.PinBarStore
import pt.aguiarvieira.xmuks.core.data.timeline.Pins
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSession
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** The room's pinned messages: which, whether we may change them, pinning, and the list itself. */
class PinActions(
    private val scope: CoroutineScope,
    private val session: RoomSession,
    private val bars: PinBarStore,
    started: SharingStarted,
) {
    val pins: StateFlow<Pins> = session.pins.stateIn(scope, started, Pins())

    /** The bar under the header: shown unless closed, and nothing has been pinned since. */
    val barShown: StateFlow<Boolean> =
        combine(pins, bars.closedOn(session.roomId)) { p, closed -> p.eventIds.any { it !in closed } }
            .stateIn(scope, started, false)

    /** Closes the bar until something else is pinned (the menu still has the pins). */
    fun closeBar() = bars.close(session.roomId, pins.value.eventIds)

    /** The pinned messages, once asked for (null while loading). */
    private val _items = MutableStateFlow<List<TimelineItem>?>(null)
    val items: StateFlow<List<TimelineItem>?> = _items.asStateFlow()

    fun toggle(eventId: String) {
        val pinned = eventId !in pins.value.eventIds
        scope.launch { session.pinning.setPinned(eventId, pinned) }
    }

    fun load() {
        _items.value = null
        scope.launch { _items.value = session.pinning.pinnedItems(pins.value.eventIds) }
    }
}
