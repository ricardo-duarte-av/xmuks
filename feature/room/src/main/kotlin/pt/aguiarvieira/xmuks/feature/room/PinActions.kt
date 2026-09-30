package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.Pins
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSession
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** The room's pinned messages: which, whether we may change them, pinning, and the list itself. */
class PinActions(
    private val scope: CoroutineScope,
    private val session: RoomSession,
    started: SharingStarted,
) {
    val pins: StateFlow<Pins> = session.pins.stateIn(scope, started, Pins())

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
