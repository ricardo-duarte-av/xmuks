package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListActions
import pt.aguiarvieira.xmuks.core.data.rooms.RoomMenuState
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import javax.inject.Inject

/** What a room's long-press menu does (see RoomMenuSheet). */
@HiltViewModel
class RoomMenuViewModel
    @Inject
    constructor(
        private val actions: RoomListActions,
    ) : ViewModel() {
        fun state(roomId: String): Flow<RoomMenuState> = actions.state(roomId)

        fun setFavourite(
            roomId: String,
            on: Boolean,
        ) = viewModelScope.launch { actions.setFavourite(roomId, on) }

        fun setLowPriority(
            roomId: String,
            on: Boolean,
        ) = viewModelScope.launch { actions.setLowPriority(roomId, on) }

        fun setMuted(
            roomId: String,
            on: Boolean,
        ) = viewModelScope.launch { actions.setMuted(roomId, on) }

        fun markRead(roomId: String) = viewModelScope.launch { actions.markRead(roomId) }

        /** Asks the launcher; [onRefused] when it can't take shortcuts. */
        fun pinShortcut(
            room: RoomSummary,
            onRefused: () -> Unit,
        ) = viewModelScope.launch { if (!actions.pinShortcut(room)) onRefused() }
    }
