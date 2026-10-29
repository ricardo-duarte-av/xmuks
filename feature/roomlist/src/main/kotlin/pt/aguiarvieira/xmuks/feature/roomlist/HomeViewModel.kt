package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.rooms.Invite
import pt.aguiarvieira.xmuks.core.data.rooms.InvitesRepository
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListActions
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import javax.inject.Inject

enum class HomeTab { Chats, Dms, Spaces }

/** An invite as the room list shows it, its avatar ready to load. */
@Immutable
data class InviteRow(
    val invite: Invite,
    val avatarUrl: String?,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        rooms: RoomListRepository,
        invites: InvitesRepository,
        private val sync: SyncController,
        store: CredentialStore,
        preferences: PreferenceStore,
        private val actions: RoomListActions,
    ) : ViewModel() {
        val search = SearchQueries()

        private val prefs = preferences.layers()
        val display: StateFlow<RoomListDisplay> =
            prefs.map(RoomListDisplay::of).stateIn(viewModelScope, WHILE_VISIBLE, RoomListDisplay())

        /** Null until the database has answered: distinguishes "loading" from "empty". */
        val chats: StateFlow<List<RoomSummary>?> =
            rooms
                .chats()
                .ordered(
                    prefs
                ).filteredBy(search.chats) { it.name }
                .stateIn(viewModelScope, WHILE_VISIBLE, null)
        val dms: StateFlow<List<RoomSummary>?> =
            rooms
                .directMessages()
                .ordered(
                    prefs
                ).filteredBy(search.dms) { it.name }
                .stateIn(viewModelScope, WHILE_VISIBLE, null)
        private val topLevelSpaces = rooms.topLevelSpaces()

        /** A dragged order, shown until gomuks has re-sorted the spaces to match (or the write failed). */
        private val pendingOrder = MutableStateFlow<List<String>?>(null)
        val spaces: StateFlow<List<SpaceSummary>?> =
            combine(topLevelSpaces, pendingOrder) { spaces, pending ->
                if (pending == null) {
                    spaces
                } else {
                    spaces.sortedBy { pending.indexOf(it.roomId).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                }
            }.filteredBy(search.spaces) { it.name }
                .stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** A space dropped so the top-level spaces read [ids]. */
        fun reorderSpaces(
            ids: List<String>,
            moved: String,
        ) {
            pendingOrder.value = ids
            viewModelScope.launch {
                if (actions.reorderSpaces(ids, moved).isSuccess) {
                    withTimeoutOrNull(RESORT_TIMEOUT_MS) {
                        topLevelSpaces.first { list ->
                            list.map { it.roomId } == ids
                        }
                    }
                }
                if (pendingOrder.value == ids) pendingOrder.value = null
            }
        }

        /** Pending invites, newest first; each tab shows its own kind. */
        val invites: StateFlow<List<InviteRow>> =
            invites
                .invites()
                .map { list -> list.map { InviteRow(it, invites.media.avatar(it.avatarMxc)) } }
                .stateIn(viewModelScope, WHILE_VISIBLE, emptyList())
        val connection: StateFlow<ConnectionState> = sync.state
        val profile: StateFlow<OwnProfile?> = rooms.ownProfile().stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** Unread, and invites waiting for an answer (as loud as a mention: someone wants us). */
        val badges: StateFlow<TabBadges> =
            combine(rooms.tabBadges(), this.invites) { badges, invites ->
                badges.copy(
                    chats = badges.chats.plusInvites(invites.count { !it.invite.isSpace }),
                    dms = badges.dms.plusInvites(invites.count { it.invite.isDirect && !it.invite.isSpace }),
                    spaces = badges.spaces.plusInvites(invites.count { it.invite.isSpace }),
                )
            }.stateIn(viewModelScope, WHILE_VISIBLE, TabBadges())
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

        private fun Unread.plusInvites(count: Int) =
            if (count == 0) this else copy(notifications = notifications + count, highlights = highlights + count)

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
            const val RESORT_TIMEOUT_MS = 10_000L
        }
    }
