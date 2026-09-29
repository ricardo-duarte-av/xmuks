package pt.aguiarvieira.xmuks.core.data.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The room on screen right now (the app in front, the room open), if any: its messages don't
 * notify, and opening it clears its notification.
 */
class OpenRoom {
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId.asStateFlow()

    /** Called when a room comes into view (and again whenever the app returns to it). */
    var onOpened: (roomId: String) -> Unit = {}

    fun opened(roomId: String) {
        _roomId.value = roomId
        onOpened(roomId)
    }

    fun closed(roomId: String) {
        _roomId.compareAndSet(roomId, null)
    }
}
