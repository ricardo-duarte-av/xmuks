package pt.aguiarvieira.xmuks.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.Invite
import pt.aguiarvieira.xmuks.core.data.rooms.InvitesRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary

/** The invite: being read, there to answer, or gone (answered, here or elsewhere, or withdrawn). */
sealed interface InviteState {
    data object Loading : InviteState

    data class Pending(
        val invite: Invite,
    ) : InviteState

    data object Gone : InviteState
}

/** The rooms we share with the inviter: still asking, the answer, or no answer to be had. */
sealed interface SharedRooms {
    data object Loading : SharedRooms

    data class Loaded(
        val rooms: List<RoomSummary>,
    ) : SharedRooms

    /** Their server doesn't say (it has to support MSC2666), or it couldn't be asked. */
    data object Unknown : SharedRooms
}

/** What answering the invite has come to. */
sealed interface InviteAnswer {
    data object Idle : InviteAnswer

    data object Working : InviteAnswer

    /** Joined, and the room has reached us: open it. */
    data class Joined(
        val roomId: String,
    ) : InviteAnswer

    data object Declined : InviteAnswer

    data class Failed(
        val message: String,
    ) : InviteAnswer
}

@HiltViewModel(assistedFactory = InviteViewModel.Factory::class)
class InviteViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        private val invites: InvitesRepository,
        private val rooms: RoomListRepository,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): InviteViewModel
        }

        val state: StateFlow<InviteState> =
            invites
                .invite(roomId)
                .map { invite -> invite?.let(InviteState::Pending) ?: InviteState.Gone }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), InviteState.Loading)

        private val _shared = MutableStateFlow<SharedRooms>(SharedRooms.Loading)
        val shared: StateFlow<SharedRooms> = _shared.asStateFlow()

        private val _answer = MutableStateFlow<InviteAnswer>(InviteAnswer.Idle)
        val answer: StateFlow<InviteAnswer> = _answer.asStateFlow()

        init {
            viewModelScope.launch {
                val invite = state.filterIsInstance<InviteState.Pending>().first().invite
                _shared.value =
                    invites.sharedRooms(invite).fold({ SharedRooms.Loaded(it) }, { SharedRooms.Unknown })
            }
        }

        fun accept() =
            answer { invite ->
                invites.accept(invite).map { joined ->
                    // The room is only ours to open once gomuks has synced it to us.
                    withTimeoutOrNull(ARRIVAL_TIMEOUT_MS) { rooms.room(joined).filterNotNull().first() }
                    InviteAnswer.Joined(joined)
                }
            }

        fun decline() = answer { invites.decline(it).map { InviteAnswer.Declined } }

        fun declineAndIgnore() = answer { invites.declineAndIgnore(it).map { InviteAnswer.Declined } }

        fun errorShown() {
            if (_answer.value is InviteAnswer.Failed) _answer.value = InviteAnswer.Idle
        }

        private fun answer(block: suspend (Invite) -> Result<InviteAnswer>) {
            val invite = (state.value as? InviteState.Pending)?.invite ?: return
            if (_answer.value == InviteAnswer.Working) return
            _answer.value = InviteAnswer.Working
            viewModelScope.launch {
                _answer.value = block(invite).getOrElse { InviteAnswer.Failed(it.message ?: it.javaClass.simpleName) }
            }
        }

        private companion object {
            const val ARRIVAL_TIMEOUT_MS = 15_000L
            const val STOP_MS = 5_000L
        }
    }
