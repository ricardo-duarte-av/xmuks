package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary

@HiltViewModel(assistedFactory = SpaceViewModel.Factory::class)
class SpaceViewModel
    @AssistedInject
    constructor(
        @Assisted val spaceId: String,
        rooms: RoomListRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(spaceId: String): SpaceViewModel
        }

        val space: StateFlow<SpaceSummary?> = rooms.space(spaceId).stateIn(viewModelScope, WHILE_VISIBLE, null)
        val subspaces: StateFlow<List<SpaceSummary>> =
            rooms
                .subspaces(
                    spaceId
                ).stateIn(viewModelScope, WHILE_VISIBLE, emptyList())

        /** Null = "All" (every room below this space); otherwise one subspace's rooms. */
        private val _filter = MutableStateFlow<String?>(null)
        val filter: StateFlow<String?> = _filter.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val rooms: StateFlow<List<RoomSummary>?> =
            _filter
                .flatMapLatest { rooms.roomsInSpace(it ?: spaceId) }
                .stateIn(viewModelScope, WHILE_VISIBLE, null)

        fun select(subspaceId: String?) {
            _filter.value = subspaceId
        }

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }
