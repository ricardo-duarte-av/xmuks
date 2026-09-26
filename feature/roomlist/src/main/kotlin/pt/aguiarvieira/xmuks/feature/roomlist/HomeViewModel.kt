package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import javax.inject.Inject

enum class HomeTab { Chats, Dms, Spaces }

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        rooms: RoomListRepository,
        private val sync: SyncController,
        private val session: SessionRepository,
        store: CredentialStore,
    ) : ViewModel() {
        /** Null until the database has answered: distinguishes "loading" from "empty". */
        val chats: StateFlow<List<RoomSummary>?> = rooms.chats().stateIn(viewModelScope, WHILE_VISIBLE, null)
        val dms: StateFlow<List<RoomSummary>?> = rooms.directMessages().stateIn(viewModelScope, WHILE_VISIBLE, null)
        val spaces: StateFlow<List<SpaceSummary>?> = rooms.topLevelSpaces().stateIn(viewModelScope, WHILE_VISIBLE, null)
        val connection: StateFlow<ConnectionState> = sync.state
        val profile: StateFlow<OwnProfile?> = rooms.ownProfile().stateIn(viewModelScope, WHILE_VISIBLE, null)
        val account: String = store.credentials()?.let { "${it.username} · ${it.serverUrl.host}" }.orEmpty()

        private val _refreshing = MutableStateFlow(false)
        val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

        /** Pull-to-refresh: a full resync; the indicator stays until the stream is live again. */
        fun refresh() {
            if (_refreshing.value) return
            _refreshing.value = true
            viewModelScope.launch {
                try {
                    sync.refresh()
                    connection.first { it is ConnectionState.Connecting || it is ConnectionState.Initializing }
                    connection.first {
                        it == ConnectionState.Live || it is ConnectionState.Retrying ||
                            it == ConnectionState.AuthFailed
                    }
                } finally {
                    _refreshing.value = false
                }
            }
        }

        fun logout() {
            viewModelScope.launch { session.logout() }
        }

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }
