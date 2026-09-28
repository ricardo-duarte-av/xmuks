package pt.aguiarvieira.xmuks.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

@HiltViewModel(assistedFactory = RoomViewModel.Factory::class)
class RoomViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        rooms: RoomListRepository,
        sessions: RoomSessions,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): RoomViewModel
        }

        private val session = sessions.open(roomId)

        val room: StateFlow<RoomSummary?> = rooms.room(roomId).stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** Newest first, for a bottom-anchored (reversed) list. Null until the first page is in. */
        val items: StateFlow<List<TimelineItem>?> =
            session.items.map { it.asReversed() }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        val loadingOlder: StateFlow<Boolean> =
            session.snapshot
                .map {
                    it.loadingOlder
                }.stateIn(viewModelScope, WHILE_VISIBLE, false)
        val hasMoreBefore: StateFlow<Boolean> =
            session.snapshot
                .map {
                    it.hasMoreBefore
                }.stateIn(viewModelScope, WHILE_VISIBLE, true)
        val typing: StateFlow<List<String>> = session.typing.stateIn(viewModelScope, WHILE_VISIBLE, emptyList())

        /** Raw events loaded (shown or not): changes with every page, even one of only hidden events. */
        val loadedEvents: StateFlow<Int> =
            session.snapshot.map { it.events.size }.stateIn(viewModelScope, WHILE_VISIBLE, 0)

        private val contextTarget = MutableStateFlow<String?>(null)

        /** A window around an older event we jumped to; null while the live timeline is shown. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val context: StateFlow<ContextView?> =
            contextTarget
                .flatMapLatest { eventId ->
                    if (eventId == null) {
                        flowOf(null)
                    } else {
                        flow {
                            emit(ContextView(eventId, items = null))
                            val snapshot = session.eventContext(eventId)
                            if (snapshot == null) {
                                emit(ContextView(eventId, items = null, failed = true))
                            } else {
                                emitAll(session.itemsOf(flowOf(snapshot)).map { ContextView(eventId, it.asReversed()) })
                            }
                        }
                    }
                }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        fun showContext(eventId: String) {
            contextTarget.value = eventId
        }

        fun leaveContext() {
            contextTarget.value = null
        }

        init {
            viewModelScope.launch { session.open() }
        }

        fun loadOlder() {
            viewModelScope.launch { session.loadOlder() }
        }

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }

/** A detached window of the timeline around [eventId] (newest first); [items] null while loading. */
data class ContextView(
    val eventId: String,
    val items: List<TimelineItem>?,
    val failed: Boolean = false,
)
