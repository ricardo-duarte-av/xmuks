package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.DataSource
import coil3.ImageLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser
import pt.aguiarvieira.xmuks.core.data.media.LinkPreviewFetcher
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.media.UPLOAD_PREFIX
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
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
        @Assisted("room") val roomId: String,
        /** One of the room's threads, shown instead of its main timeline. */
        @Assisted("thread") val threadRoot: String?,
        rooms: RoomListRepository,
        private val sessions: RoomSessions,
        drafts: DraftStore,
        profiles: ProfileRepository,
        preparer: MediaPreparer,
        private val uploads: MediaSender,
        @Named("player") playerSource: DataSource.Factory,
        /** Timeline pictures' own cache tier. */
        @Named("media") val mediaImages: ImageLoader,
        @ApplicationContext context: Context,
        private val openRoom: OpenRoom,
        val media: MediaUrls,
        linkPreviews: LinkPreviewFetcher,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("room") roomId: String,
                @Assisted("thread") threadRoot: String?,
            ): RoomViewModel
        }

        private val session = sessions.open(roomId)

        /** The thread, when that's what's shown. */
        private val thread = threadRoot?.let(session::thread)

        /** Where what's shown comes from: the room's timeline, or the thread's. */
        private val timeline = thread?.snapshot ?: session.snapshot

        val room: StateFlow<RoomSummary?> = rooms.room(roomId).stateIn(viewModelScope, WHILE_VISIBLE, null)

        private val marker = MutableStateFlow<UnreadMarker?>(null)

        /** Where reading stopped when the room was opened (null when nothing was unread). */
        val unread: StateFlow<UnreadMarker?> =
            combine(marker, timeline) { m, snapshot ->
                m?.copy(timestamp = snapshot.events.firstOrNull { it.eventId == m.eventId }?.timestamp)
            }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** Newest first, for a bottom-anchored (reversed) list. Null until the first page is in. */
        val items: StateFlow<List<TimelineItem>?> =
            combine(thread?.let { session.itemsOf(it.snapshot) } ?: session.items, unread) { items, u ->
                items.asReversed().withUnreadSeparator(u?.timestamp)
            }.stateIn(viewModelScope, WHILE_VISIBLE, null)

        val loadingOlder: StateFlow<Boolean> =
            timeline
                .map {
                    it.loadingOlder
                }.stateIn(viewModelScope, WHILE_VISIBLE, false)
        val hasMoreBefore: StateFlow<Boolean> =
            timeline
                .map {
                    it.hasMoreBefore
                }.stateIn(viewModelScope, WHILE_VISIBLE, true)
        val typing: StateFlow<List<String>> = session.typing.stateIn(viewModelScope, WHILE_VISIBLE, emptyList())

        /** Raw events loaded (shown or not): changes with every page, even one of only hidden events. */
        val loadedEvents: StateFlow<Int> =
            timeline.map { it.events.size }.stateIn(viewModelScope, WHILE_VISIBLE, 0)

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

        /** gomuks' preferences as they apply here. */
        val prefs: StateFlow<PrefLayers> =
            session.preferences.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                PrefLayers.EMPTY
            )

        /** A command refused before it was sent (hide fingerprint: no bot commands). */
        val refusal = OneShot<Int>()

        /** Our per-message profiles here, and which one messages go out as. */
        val personas = PersonaActions(viewModelScope, roomId, profiles, rooms.ownProfile(), WHILE_VISIBLE)

        /** Voice messages, audio and videos playing in their bubbles. */
        val player = InlinePlayer(context, playerSource, viewModelScope)

        /** Attachments: the preview step, then the upload. */
        val attach =
            MediaActions(
                viewModelScope,
                roomId,
                preparer,
                uploads,
                encrypted = { room.value?.encrypted == true },
                replyTo = { target.reply() },
                onSent = { if (modes.mode.value is ComposeMode.Reply) modes.cancel() },
                showDialog = { prefs.value.get(Prefs.uploadDialog) },
            )

        /** Pinned messages: which, the list, pinning and unpinning. */
        val pins = PinActions(viewModelScope, session, WHILE_VISIBLE)

        val polls = pollActions(viewModelScope, session.polls)

        /** Reacting, stickers, recent emoji and pack subscriptions. */
        val emoji = EmojiActions(viewModelScope, session, WHILE_VISIBLE) { target.reply() }

        /** Whether the next send is a new message, a reply or an edit. */
        val modes = ComposeModes(draft)

        /** Previews offered for the links being written, bundled when sent. */
        internal val linkPreviews =
            ComposerPreviews(
                viewModelScope,
                draft,
                combine(prefs, modes.mode) { p, mode ->
                    // gomuks web leaves them out with the fingerprint hidden; edits don't take them.
                    p.get(Prefs.sendBundledUrlPreviews) && !p.get(Prefs.hideFingerprint) && mode !is ComposeMode.Edit
                },
                { room.value?.encrypted == true },
                linkPreviews,
            )

        /** Where what's sent goes: replies, and into the thread when that's what's shown. */
        private val target = SendTarget(modes, threadRoot) { items.value }

        /** A thread keeps its own draft, apart from the room's. */
        private val draftKeeper =
            DraftKeeper(
                viewModelScope,
                threadRoot?.let { "$roomId#$it" } ?: roomId,
                draft,
                { modes.draftText },
                drafts,
                modes,
                items,
            )

        /** Slash commands usable in this room (gomuks' built-ins, text prefixes, the room's bots). */
        val commands: StateFlow<List<BotCommand>> = session.commands.stateIn(viewModelScope, WHILE_VISIBLE, emptyList())

        /** Hands the draft to the outbox (it survives the app dying) and clears the field. */
        fun send() {
            val text = draft.text.toString().trim()
            if (text.isEmpty()) return
            if (sendCommand(text)) return
            val mode = modes.mode.value
            val outgoing = emoji.expandShortcodes(text)
            // Taken before the reply mode ends with the send.
            val replyTo = target.reply()
            val previews = linkPreviews.take()
            modes.sent()
            stopTyping()
            viewModelScope.launch {
                when (mode) {
                    ComposeMode.New, is ComposeMode.Reply -> {
                        session.writer.send(outgoing, replyTo = replyTo, previews = previews)
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
            // As gomuks: with hide fingerprint on, bots' commands aren't sent at all (built-ins are).
            if (command.source != BotCommand.GOMUKS && prefs.value.get(Prefs.hideFingerprint)) {
                refusal.show(R.string.command_fingerprint)
                return true
            }
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
            if (thread != null || eventId == lastMarked) return
            lastMarked = eventId
            viewModelScope.launch { session.writer.markRead(eventId) }
        }

        private val typingNotifier = TypingNotifier(viewModelScope, draft, session.writer::setTyping)

        private fun stopTyping() = typingNotifier.stop()

        fun sendLocation(location: PickedLocation) {
            val reply = target.reply()
            if (modes.mode.value is ComposeMode.Reply) modes.cancel()
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
            thread?.let { viewModelScope.launch { it.open() } }
            // Taken once, before reading at the bottom moves the marker on.
            viewModelScope.launch {
                val count =
                    rooms
                        .room(roomId)
                        .first()
                        ?.unread
                        ?.messages ?: 0
                if (count > 0 && thread == null) session.readMarker()?.let { marker.value = UnreadMarker(it, count) }
            }
        }

        private var shown = false

        /** The in-room blip for others' new messages (while the room is on screen). */
        private val blip = NewMessageSound(context, viewModelScope, items) { shown }

        /** On screen (the app in front) or not: while it is, its messages don't notify, and its notification goes. */
        fun onScreen(shown: Boolean) {
            this.shown = shown
            if (shown) openRoom.opened(roomId) else openRoom.closed(roomId)
        }

        fun loadOlder() {
            viewModelScope.launch { thread?.loadOlder() ?: session.loadOlder() }
        }

        override fun onCleared() {
            player.release()
            blip.release()
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
