package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary

/** M3 placeholder for the room screen: its header, so the list → room transition is real. */
@HiltViewModel(assistedFactory = RoomViewModel.Factory::class)
class RoomViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        rooms: RoomListRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): RoomViewModel
        }

        val room: StateFlow<RoomSummary?> =
            rooms
                .room(
                    roomId
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
