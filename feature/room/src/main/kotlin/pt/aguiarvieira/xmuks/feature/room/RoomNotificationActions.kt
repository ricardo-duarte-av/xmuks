package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules

/** How this room notifies (its push rules), and changing it. */
class RoomNotificationActions(
    private val scope: CoroutineScope,
    private val roomId: String,
    private val rules: RoomPushRules,
    started: SharingStarted,
) {
    /** What we last set: shown until the synced rules agree (gomuks' copy can lag well behind). */
    private val chosen = MutableStateFlow<RoomNotifications?>(null)

    val setting: StateFlow<RoomNotifications> =
        combine(rules.setting(roomId), chosen) { synced, mine -> mine ?: synced }
            .stateIn(scope, started, RoomNotifications.Default)

    /** Why the last change failed, for the screen to say; cleared once shown. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun set(setting: RoomNotifications) {
        scope.launch {
            val error = rules.set(roomId, setting)
            if (error == null) chosen.value = setting else _error.value = error
        }
    }

    fun errorShown() {
        _error.value = null
    }
}
