package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.CallParticipant
import pt.aguiarvieira.xmuks.core.call.CallPhase
import pt.aguiarvieira.xmuks.core.call.CallSession
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfiles
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository

/** A participant as the call screen draws them. */
@Immutable
data class CallTile(
    val participant: CallParticipant,
    val name: String,
    val avatarUrl: String?,
)

@Immutable
data class CallUi(
    val roomName: String = "",
    val roomAvatarUrl: String? = null,
    val isDirect: Boolean = false,
    /** Null until this room's call is the active one. */
    val phase: CallPhase? = null,
    val tiles: List<CallTile> = emptyList(),
    val microphoneOn: Boolean = true,
    val cameraOn: Boolean = false,
    val connectedAt: Long? = null,
) {
    val local: CallTile? get() = tiles.firstOrNull { it.participant.isLocal }
    val remote: List<CallTile> get() = tiles.filterNot { it.participant.isLocal }

    /** Anyone's camera is on: the video layouts take over from the avatar ones. */
    val anyVideo: Boolean get() = tiles.any { it.participant.video != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = CallViewModel.Factory::class)
class CallViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        private val manager: CallManager,
        private val profiles: RoomProfiles,
        private val media: MediaUrls,
        rooms: RoomListRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): CallViewModel
        }

        private val names = MutableStateFlow<Map<String, Pair<String, String?>>>(emptyMap())

        private val session =
            manager.active
                .stateIn(viewModelScope, SharingStarted.Eagerly, manager.active.value)

        val ui: StateFlow<CallUi> =
            combine(
                rooms.room(roomId),
                session.flatMapLatest { s -> s?.takeIf { it.room.roomId == roomId }?.let(::sessionUi) ?: flowOf(null) },
                names,
            ) { room, live, known ->
                val base =
                    CallUi(
                        roomName = room?.name.orEmpty(),
                        roomAvatarUrl = room?.avatarUrl,
                        isDirect = room?.isDirect == true,
                    )
                if (live == null) {
                    base
                } else {
                    base.copy(
                        phase = live.phase,
                        tiles =
                            live.participants.map { p ->
                                val (name, avatar) = known[p.userId] ?: (p.userId to null)
                                CallTile(p, name, avatar)
                            },
                        microphoneOn = live.microphoneOn,
                        cameraOn = live.cameraOn,
                        connectedAt = live.connectedAt,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), CallUi())

        private data class Live(
            val phase: CallPhase,
            val participants: List<CallParticipant>,
            val microphoneOn: Boolean,
            val cameraOn: Boolean,
            val connectedAt: Long?,
        )

        private fun sessionUi(s: CallSession) =
            combine(
                s.phase,
                s.participants.onEach(::resolveNames),
                s.microphoneOn,
                s.cameraOn
            ) { phase, people, mic, cam ->
                Live(phase, people, mic, cam, s.connectedAt)
            }

        private fun resolveNames(people: List<CallParticipant>) {
            val missing = people.map { it.userId }.distinct().filterNot { it in names.value }
            if (missing.isEmpty()) return
            names.value = names.value + missing.associateWith { (it to null) }
            viewModelScope.launch {
                missing.forEach { userId ->
                    val profile = profiles.of(roomId, userId) ?: return@forEach
                    names.value =
                        names.value + (userId to ((profile.displayName ?: userId) to media.avatar(profile.avatarMxc)))
                }
            }
        }

        /** Joins this room's call unless we're already in it. */
        fun join(video: Boolean) = manager.join(roomId, video)

        fun setMicrophone(on: Boolean) = session.value?.setMicrophone(on)

        fun setCamera(on: Boolean) = session.value?.setCamera(on)

        fun flipCamera() = session.value?.flipCamera()

        fun hangUp() = manager.hangUp()

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
