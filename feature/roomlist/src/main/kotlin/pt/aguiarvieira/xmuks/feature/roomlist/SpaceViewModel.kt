package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.rooms.InvitesRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceEditCandidate
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceEditRepository
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges

/** The space editor while it's open: whose children, what they were when it opened, what they'll be. */
data class SpaceEditSession(
    val targetId: String,
    val initial: Set<String>,
    val selected: Set<String>,
    val saving: Boolean = false,
) {
    val added: Set<String> get() = selected - initial
    val removed: Set<String> get() = initial - selected
    val changes: Int get() = added.size + removed.size

    fun toggle(roomId: String) = copy(selected = if (roomId in selected) selected - roomId else selected + roomId)

    /** After a save: what went through is the new starting point; what failed stays pending. */
    fun saved(failed: Set<String>) = copy(initial = initial + (added - failed) - (removed - failed), saving = false)
}

/** A room in the editor's list. [wasIn]: a child when editing began (it decides the section). */
data class SpaceEditRow(
    val room: SpaceEditCandidate,
    val wasIn: Boolean,
)

/** What a save has to say. */
sealed interface SpaceEditNotice {
    data object Saved : SpaceEditNotice

    data class Failed(
        val count: Int,
    ) : SpaceEditNotice

    data class ParentKept(
        val count: Int,
    ) : SpaceEditNotice
}

@HiltViewModel(assistedFactory = SpaceViewModel.Factory::class)
class SpaceViewModel
    @AssistedInject
    constructor(
        @Assisted val spaceId: String,
        rooms: RoomListRepository,
        invites: InvitesRepository,
        private val preferences: PreferenceStore,
        private val spaceEdits: SpaceEditRepository,
        roomInfo: RoomInfoRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(spaceId: String): SpaceViewModel
        }

        val display: StateFlow<RoomListDisplay> =
            preferences.layers().map(RoomListDisplay::of).stateIn(viewModelScope, WHILE_VISIBLE, RoomListDisplay())

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
                .ordered(preferences.layers())
                .stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** The tab bar's badges, as home has them. */
        val badges: StateFlow<TabBadges> =
            combine(rooms.tabBadges(), invites.invites(), TabBadges::withInvites)
                .stateIn(viewModelScope, WHILE_VISIBLE, TabBadges())

        fun select(subspaceId: String?) {
            _filter.value = subspaceId
        }

        /** The space an edit changes: the selected subspace, or this space under "All". */
        private val target: String get() = _filter.value ?: spaceId

        /** Whether we may change the shown space's children (rechecked when its power levels change). */
        @OptIn(ExperimentalCoroutinesApi::class)
        val canEdit: StateFlow<Boolean> =
            _filter
                .flatMapLatest { selected ->
                    val id = selected ?: spaceId
                    roomInfo.stateChanges(id).mapLatest { spaceEdits.canEdit(id) }
                }.stateIn(viewModelScope, WHILE_VISIBLE, false)

        private val _edit = MutableStateFlow<SpaceEditSession?>(null)

        /** The open editor, or null. */
        val edit: StateFlow<SpaceEditSession?> = _edit.asStateFlow()

        val editQuery = TextFieldState()

        /** The editor's list: rooms in the space when editing began first, then the rest, filtered. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val editRows: StateFlow<List<SpaceEditRow>?> =
            _edit
                .map { it?.let { session -> session.targetId to session.initial } }
                .distinctUntilChanged()
                .flatMapLatest { opened ->
                    if (opened == null) return@flatMapLatest flowOf(null)
                    val (targetId, initial) = opened
                    candidates(targetId).filteredBy(editQuery) { it.name }.map { found ->
                        val (inside, outside) = found.partition { it.roomId in initial }
                        (inside + outside).map { SpaceEditRow(it, it.roomId in initial) }
                    }
                }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        private fun candidates(targetId: String): Flow<List<SpaceEditCandidate>> =
            spaceEdits.data(targetId).map { it.candidates }

        private val _notices = Channel<SpaceEditNotice>(Channel.BUFFERED)
        val notices: Flow<SpaceEditNotice> = _notices.receiveAsFlow()

        fun startEditing() {
            if (_edit.value != null) return
            val targetId = target
            viewModelScope.launch {
                val children = spaceEdits.data(targetId).first().children
                editQuery.clearText()
                _edit.value = SpaceEditSession(targetId, children, children)
            }
        }

        fun toggle(roomId: String) {
            val session = _edit.value ?: return
            if (!session.saving) _edit.value = session.toggle(roomId)
        }

        /** Back: the editor closes and nothing is sent. Not while a save is on its way. */
        fun cancelEditing() {
            if (_edit.value?.saving == true) return
            _edit.value = null
            editQuery.clearText()
        }

        fun save() {
            val session = _edit.value ?: return
            if (session.saving) return
            if (session.changes == 0) return cancelEditing()
            _edit.value = session.copy(saving = true)
            viewModelScope.launch {
                val result = spaceEdits.save(session.targetId, session.added, session.removed)
                if (result.failed.isEmpty()) {
                    _edit.value = null
                    editQuery.clearText()
                    _notices.send(SpaceEditNotice.Saved)
                } else {
                    _edit.value = session.saved(result.failed)
                    _notices.send(SpaceEditNotice.Failed(result.failed.size))
                }
                if (result.parentKept.isNotEmpty()) _notices.send(SpaceEditNotice.ParentKept(result.parentKept.size))
            }
        }

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }
