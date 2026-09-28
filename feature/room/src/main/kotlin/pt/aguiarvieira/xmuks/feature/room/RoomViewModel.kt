package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

@HiltViewModel(assistedFactory = RoomViewModel.Factory::class)
class RoomViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        rooms: RoomListRepository,
        private val sessions: RoomSessions,
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
                        flowOf<ContextView?>(null)
                    } else {
                        flow<ContextView?> {
                            emit(ContextView(eventId, items = null))
                            val snapshot = session.eventContext(eventId)
                            if (snapshot == null) {
                                emit(ContextView(eventId, items = null, failed = true))
                            } else {
                                emitAll(
                                    session.itemsOf(flowOf(snapshot)).map<List<TimelineItem>, ContextView?> {
                                        ContextView(eventId, it.asReversed())
                                    },
                                )
                            }
                        }
                    }
                }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** What's being written. Survives rotation with the view model; kept per open room. */
        val draft = TextFieldState()

        private val _mode = MutableStateFlow<ComposeMode>(ComposeMode.New)

        /** Whether the next send is a new message, a reply or an edit. */
        val mode: StateFlow<ComposeMode> = _mode

        /** Hands the draft to the outbox (it survives the app dying) and clears the field. */
        fun send() {
            val text = draft.text.toString().trim()
            if (text.isEmpty()) return
            val mode = _mode.value
            draft.clearText()
            _mode.value = ComposeMode.New
            stopTyping()
            viewModelScope.launch {
                when (mode) {
                    ComposeMode.New -> {
                        session.send(text)
                    }

                    is ComposeMode.Reply -> {
                        session.send(
                            text,
                            replyTo = ReplyTarget(mode.message.eventId, mode.message.sender)
                        )
                    }

                    is ComposeMode.Edit -> {
                        session.send(text, editing = mode.message.eventId)
                    }
                }
            }
        }

        fun reply(message: TimelineItem.Message) {
            if (_mode.value is ComposeMode.Edit) draft.clearText()
            _mode.value = ComposeMode.Reply(message)
        }

        /** Puts one of our messages back in the composer to be edited. */
        fun edit(message: TimelineItem.Message) {
            val source = message.editSource ?: return
            _mode.value = ComposeMode.Edit(message)
            draft.setTextAndPlaceCursorAtEnd(source)
        }

        fun cancelMode() {
            if (_mode.value is ComposeMode.Edit) draft.clearText()
            _mode.value = ComposeMode.New
        }

        private var lastMarked: String? = null

        /** The newest message was on screen: mark the room read up to it (once per event). */
        fun markRead(eventId: String) {
            if (eventId == lastMarked) return
            lastMarked = eventId
            viewModelScope.launch { session.markRead(eventId) }
        }

        private var typingSentAt = 0L

        /**
         * While the draft has text, tell the room we're typing (renewed every few seconds, like
         * gomuks web); stop as soon as it's empty or sent.
         */
        private fun watchTyping() {
            viewModelScope.launch {
                snapshotFlow { draft.text.isNotEmpty() }.collect { hasText ->
                    if (!hasText) stopTyping()
                }
            }
            viewModelScope.launch {
                snapshotFlow { draft.text.toString() }.collect { text ->
                    val now = System.currentTimeMillis()
                    if (text.isNotEmpty() && now - typingSentAt > TYPING_RENEW_MS) {
                        typingSentAt = now
                        session.setTyping(TYPING_TIMEOUT_MS)
                    }
                }
            }
        }

        private fun stopTyping() {
            if (typingSentAt == 0L) return
            typingSentAt = 0L
            viewModelScope.launch { session.setTyping(0) }
        }

        fun resend(localId: String) {
            viewModelScope.launch { session.resend(localId) }
        }

        fun discard(localId: String) {
            viewModelScope.launch { session.discard(localId) }
        }

        fun showContext(eventId: String) {
            contextTarget.value = eventId
        }

        fun leaveContext() {
            contextTarget.value = null
        }

        init {
            viewModelScope.launch { session.open() }
            watchTyping()
        }

        fun loadOlder() {
            viewModelScope.launch { session.loadOlder() }
        }

        override fun onCleared() {
            // Leaving the room: we're not typing any more. (The session's scope outlives this one.)
            if (typingSentAt != 0L) sessions.stopTyping(roomId)
        }

        private companion object {
            const val TYPING_TIMEOUT_MS = 10_000
            const val TYPING_RENEW_MS = 5_000L
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }

/** A detached window of the timeline around [eventId] (newest first); [items] null while loading. */
data class ContextView(
    val eventId: String,
    val items: List<TimelineItem>?,
    val failed: Boolean = false,
)
