package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.media.UPLOAD_PREFIX
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import javax.inject.Named

@HiltViewModel(assistedFactory = RoomViewModel.Factory::class)
class RoomViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        rooms: RoomListRepository,
        private val sessions: RoomSessions,
        drafts: DraftStore,
        profiles: ProfileRepository,
        preparer: MediaPreparer,
        private val uploads: MediaSender,
        @Named("media") mediaHttp: OkHttpClient,
        @ApplicationContext context: Context,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): RoomViewModel
        }

        private val session = sessions.open(roomId)

        val room: StateFlow<RoomSummary?> = rooms.room(roomId).stateIn(viewModelScope, WHILE_VISIBLE, null)

        private val marker = MutableStateFlow<UnreadMarker?>(null)

        /** Where reading stopped when the room was opened (null when nothing was unread). */
        val unread: StateFlow<UnreadMarker?> =
            combine(marker, session.snapshot) { m, snapshot ->
                m?.copy(timestamp = snapshot.events.firstOrNull { it.eventId == m.eventId }?.timestamp)
            }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** Newest first, for a bottom-anchored (reversed) list. Null until the first page is in. */
        val items: StateFlow<List<TimelineItem>?> =
            combine(session.items, unread) { items, u -> items.asReversed().withUnreadSeparator(u?.timestamp) }
                .stateIn(viewModelScope, WHILE_VISIBLE, null)

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
                                val markerTs =
                                    marker.value?.let { m ->
                                        snapshot.events.firstOrNull { it.eventId == m.eventId }?.timestamp
                                    }
                                emitAll(
                                    session.itemsOf(flowOf(snapshot)).map<List<TimelineItem>, ContextView?> {
                                        ContextView(eventId, it.asReversed().withUnreadSeparator(markerTs))
                                    },
                                )
                            }
                        }
                    }
                }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** What's being written; kept per room when the room is left, and across restarts. */
        val draft = TextFieldState()

        /** Our per-message profiles here, and which one messages go out as. */
        val personas = PersonaActions(viewModelScope, roomId, profiles, rooms.ownProfile(), WHILE_VISIBLE)

        /** Voice messages, audio and videos playing in their bubbles. */
        val player = InlinePlayer(context, mediaHttp, viewModelScope)

        /** Attachments: the preview step, then the upload. */
        val attach =
            MediaActions(
                viewModelScope,
                roomId,
                preparer,
                uploads,
                encrypted = { room.value?.encrypted == true },
                replyTo = {
                    (modes.mode.value as? ComposeMode.Reply)?.message?.let {
                        ReplyTarget(
                            it.eventId,
                            it.sender
                        )
                    }
                },
                onSent = { if (modes.mode.value is ComposeMode.Reply) modes.cancel() },
            )

        /** Reacting, stickers, recent emoji and pack subscriptions. */
        val emoji = EmojiActions(viewModelScope, session, WHILE_VISIBLE)

        /** Whether the next send is a new message, a reply or an edit. */
        val modes = ComposeModes(draft)

        private val draftKeeper = DraftKeeper(viewModelScope, roomId, draft, { modes.draftText }, drafts)

        /** Slash commands usable in this room (gomuks' built-ins, text prefixes, the room's bots). */
        val commands: StateFlow<List<BotCommand>> = session.commands.stateIn(viewModelScope, WHILE_VISIBLE, emptyList())

        /** Hands the draft to the outbox (it survives the app dying) and clears the field. */
        fun send() {
            val text = draft.text.toString().trim()
            if (text.isEmpty()) return
            if (sendCommand(text)) return
            val mode = modes.mode.value
            val outgoing = emoji.expandShortcodes(text)
            modes.sent()
            stopTyping()
            viewModelScope.launch {
                when (mode) {
                    ComposeMode.New -> {
                        session.writer.send(outgoing)
                    }

                    is ComposeMode.Reply -> {
                        session.writer.send(
                            outgoing,
                            replyTo = ReplyTarget(mode.message.eventId, mode.message.sender)
                        )
                    }

                    is ComposeMode.Edit -> {
                        session.writer.send(outgoing, editing = mode.message.eventId)
                    }
                }
            }
        }

        /**
         * A structured command (a gomuks built-in, or a room bot's): sent with typed arguments to
         * whoever runs it. Text prefixes (`/me`, `/rainbow`…) and unknown commands go as text, for
         * gomuks to handle or refuse.
         */
        private fun sendCommand(text: String): Boolean {
            if (!text.startsWith("/") || text.startsWith("//")) return false
            val command = CommandParser.match(text, commands.value)?.takeUnless { it.textPrefix } ?: return false
            val arguments = CommandParser.parse(command, text) ?: return false
            modes.sent()
            stopTyping()
            viewModelScope.launch { session.writer.sendCommand(command, arguments, text) }
            return true
        }

        private val historyLoader = HistoryLoader(viewModelScope, session)

        /** A message's edit history, or a deleted message's content, being shown. */
        val history: StateFlow<HistoryView?> = historyLoader.shown

        fun showHistory(message: TimelineItem.Message) = historyLoader.show(message)

        fun hideHistory() = historyLoader.hide()

        fun delete(message: TimelineItem.Message) {
            viewModelScope.launch { session.writer.redact(message.eventId) }
        }

        private var lastMarked: String? = null

        /** The newest message was on screen: mark the room read up to it (once per event). */
        fun markRead(eventId: String) {
            if (eventId == lastMarked) return
            lastMarked = eventId
            viewModelScope.launch { session.writer.markRead(eventId) }
        }

        private val typingNotifier = TypingNotifier(viewModelScope, draft, session.writer::setTyping)

        private fun stopTyping() = typingNotifier.stop()

        fun sendLocation(location: PickedLocation) {
            val reply = (modes.mode.value as? ComposeMode.Reply)?.message?.let { ReplyTarget(it.eventId, it.sender) }
            if (reply != null) modes.cancel()
            viewModelScope.launch {
                session.writer.sendLocation(
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    location.self,
                    reply
                )
            }
        }

        fun resend(localId: String) {
            if (localId.startsWith(UPLOAD_PREFIX)) return uploads.retry(localId.removePrefix(UPLOAD_PREFIX))
            viewModelScope.launch { session.writer.resend(localId) }
        }

        fun discard(localId: String) {
            if (localId.startsWith(UPLOAD_PREFIX)) return uploads.discard(localId.removePrefix(UPLOAD_PREFIX))
            viewModelScope.launch { session.writer.discard(localId) }
        }

        fun showContext(eventId: String) {
            contextTarget.value = eventId
        }

        fun leaveContext() {
            contextTarget.value = null
        }

        init {
            viewModelScope.launch { session.open() }
            // Taken once, before reading at the bottom moves the marker on.
            viewModelScope.launch {
                val count =
                    rooms
                        .room(roomId)
                        .first()
                        ?.unread
                        ?.messages ?: 0
                if (count > 0) session.readMarker()?.let { marker.value = UnreadMarker(it, count) }
            }
        }

        fun loadOlder() {
            viewModelScope.launch { session.loadOlder() }
        }

        override fun onCleared() {
            player.release()
            draftKeeper.flush()
            // Leaving the room: we're not typing any more. (The session's scope outlives this one.)
            if (typingNotifier.active) sessions.stopTyping(roomId)
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
