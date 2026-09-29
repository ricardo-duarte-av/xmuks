package pt.aguiarvieira.xmuks.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomPreview
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository

/** A room we're not in, as its summary shows it: loading, shown, or why it can't be. */
sealed interface PreviewState {
    data object Loading : PreviewState

    data class Loaded(
        val preview: RoomPreview,
    ) : PreviewState

    /** No preview (a private room, or its server doesn't answer): joining can still be tried. */
    data class Failed(
        val message: String,
    ) : PreviewState
}

/** What joining or knocking has come to. */
sealed interface JoinState {
    data object Idle : JoinState

    data object Working : JoinState

    /** Joined, and the room has reached us: open it. */
    data class Joined(
        val roomId: String,
    ) : JoinState

    data object Knocked : JoinState

    data class Failed(
        val message: String,
    ) : JoinState
}

@HiltViewModel(assistedFactory = RoomPreviewViewModel.Factory::class)
class RoomPreviewViewModel
    @AssistedInject
    constructor(
        @Assisted("room") val roomIdOrAlias: String,
        @Assisted("via") private val via: List<String>,
        private val rooms: RoomInfoRepository,
        private val roomList: RoomListRepository,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("room") roomIdOrAlias: String,
                @Assisted("via") via: List<String>,
            ): RoomPreviewViewModel
        }

        private val _state = MutableStateFlow<PreviewState>(PreviewState.Loading)
        val state: StateFlow<PreviewState> = _state.asStateFlow()

        private val _join = MutableStateFlow<JoinState>(JoinState.Idle)
        val join: StateFlow<JoinState> = _join.asStateFlow()

        init {
            viewModelScope.launch {
                rooms
                    .summary(roomIdOrAlias, via)
                    .onSuccess { preview ->
                        _state.value = PreviewState.Loaded(preview)
                        // Already in it (joined elsewhere since the link was made): straight in.
                        if (preview.membership == "join") _join.value = JoinState.Joined(preview.roomId)
                    }.onFailure { _state.value = PreviewState.Failed(it.message.orEmpty()) }
            }
        }

        fun join(reason: String? = null) =
            act {
                rooms.join(target(), via, reason).map { roomId ->
                    // The room is only ours to open once gomuks has synced it to us.
                    withTimeoutOrNull(ARRIVAL_TIMEOUT_MS) { roomList.room(roomId).filterNotNull().first() }
                    JoinState.Joined(roomId)
                }
            }

        fun knock(reason: String?) = act { rooms.knock(target(), via, reason).map { JoinState.Knocked } }

        fun errorShown() {
            if (_join.value is JoinState.Failed) _join.value = JoinState.Idle
        }

        /** The ID once we know it (more reliable than an alias that may move), else what the link said. */
        private fun target() = (_state.value as? PreviewState.Loaded)?.preview?.roomId ?: roomIdOrAlias

        private fun act(block: suspend () -> Result<JoinState>) {
            if (_join.value == JoinState.Working) return
            _join.value = JoinState.Working
            viewModelScope.launch {
                _join.value = block().getOrElse { JoinState.Failed(it.message ?: it.javaClass.simpleName) }
            }
        }

        private companion object {
            const val ARRIVAL_TIMEOUT_MS = 15_000L
        }
    }
