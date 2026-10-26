package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryRoom
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryUnsupported
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository

/** Our server's directory as far as it's loaded, and what's asked of it. */
data class DirectoryState(
    /** Our server's name (from our Matrix ID); null until known. */
    val server: String? = null,
    val spaces: Boolean = false,
    val rooms: List<DirectoryRoom> = emptyList(),
    /** Those of [rooms] we're in. */
    val joined: Set<String> = emptySet(),
    val next: String? = null,
    val loading: Boolean = true,
    /** gomuks is too old to have the directory. */
    val unsupported: Boolean = false,
    val error: String? = null,
    /** The room whose menu is open, and whether we may take it off the directory (null: finding out). */
    val menu: DirectoryMenu? = null,
)

data class DirectoryMenu(
    val room: DirectoryRoom,
    val canRemove: Boolean? = null,
)

@HiltViewModel(assistedFactory = DirectoryViewModel.Factory::class)
class DirectoryViewModel
    @AssistedInject
    constructor(
        @Assisted spaces: Boolean,
        private val directory: RoomDirectoryRepository,
        private val roomInfo: RoomInfoRepository,
        private val roomList: RoomListRepository,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(spaces: Boolean): DirectoryViewModel
        }

        val text = TextFieldState()
        private val _state = MutableStateFlow(DirectoryState(spaces = spaces))
        val state: StateFlow<DirectoryState> = _state.asStateFlow()

        /** For a toast; cleared once shown. */
        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        private var running: Job? = null

        init {
            viewModelScope.launch {
                val me = roomInfo.me.filterNotNull().first()
                _state.update { it.copy(server = me.substringAfter(':')) }
            }
            reload()
            viewModelScope.launch {
                // The first value is the empty field, already loaded above.
                snapshotFlow { text.text.toString().trim() }.distinctUntilChanged().drop(1).collectLatest {
                    delay(DEBOUNCE_MS)
                    reload()
                }
            }
        }

        fun spacesOnly(on: Boolean) {
            if (on == _state.value.spaces) return
            _state.update { it.copy(spaces = on) }
            reload()
        }

        /** The first page again, for what's asked now. */
        fun reload() {
            running?.cancel()
            _state.update {
                it.copy(rooms = emptyList(), joined = emptySet(), next = null, loading = true, error = null)
            }
            running = viewModelScope.launch { load(null) }
        }

        fun loadMore() {
            val now = _state.value
            val next = now.next ?: return
            if (now.loading) return
            _state.update { it.copy(loading = true) }
            running = viewModelScope.launch { load(next) }
        }

        private suspend fun load(since: String?) {
            val asked = _state.value
            directory
                .page(text.text.toString(), asked.spaces, since)
                .onSuccess { page ->
                    val joined = roomList.rooms(page.rooms.map { it.roomId }).map { it.roomId }
                    _state.update {
                        it.copy(
                            rooms = (it.rooms + page.rooms).distinctBy { room -> room.roomId },
                            joined = it.joined + joined,
                            next = page.nextBatch,
                            loading = false,
                        )
                    }
                }.onFailure { e ->
                    _state.update {
                        it.copy(loading = false, unsupported = e is DirectoryUnsupported, error = e.message)
                    }
                }
        }

        /** Opens [room]'s menu, finding out meanwhile whether we may take it off the directory. */
        fun openMenu(room: DirectoryRoom) {
            _state.update { it.copy(menu = DirectoryMenu(room)) }
            viewModelScope.launch {
                val canRemove = room.roomId in _state.value.joined && mayList(room.roomId)
                _state.update { s ->
                    s.copy(menu = s.menu?.takeIf { it.room.roomId == room.roomId }?.copy(canRemove = canRemove))
                }
            }
        }

        fun closeMenu() = _state.update { it.copy(menu = null) }

        /** Takes [room] off the directory; it leaves the list once it has. */
        fun remove(room: DirectoryRoom) {
            viewModelScope.launch {
                directory
                    .setListed(room.roomId, false)
                    .onSuccess {
                        _state.update { s -> s.copy(rooms = s.rooms.filterNot { it.roomId == room.roomId }) }
                    }.onFailure { _message.value = it.message ?: it.javaClass.simpleName }
            }
        }

        fun messageShown() {
            _message.value = null
        }

        /** As servers decide it (Synapse): changing the directory takes the power to change the room's address. */
        private suspend fun mayList(roomId: String): Boolean {
            val me = roomInfo.me.first() ?: return false
            return roomInfo.powerLevels(roomId).getOrNull()?.canSetState(me, CANONICAL_ALIAS) == true
        }

        private companion object {
            const val DEBOUNCE_MS = 400L
            const val CANONICAL_ALIAS = "m.room.canonical_alias"
        }
    }
