package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * Keeps [draft] across leaving the room (and app restarts): puts back what was saved when the room
 * opens, and saves what's typed shortly after typing pauses, and once more on [flush].
 */
internal class DraftKeeper(
    scope: CoroutineScope,
    private val roomId: String,
    private val draft: TextFieldState,
    /** What to keep: the typed text, or while editing a message, the draft set aside for it. */
    private val draftText: () -> String,
    private val store: DraftStore,
    /** Reply mode is kept too: the message answered, found again among [items] when it's loaded. */
    private val modes: ComposeModes? = null,
    private val items: Flow<List<TimelineItem>?> = emptyFlow(),
) {
    init {
        scope.launch { keepReply() }
        scope.launch {
            val saved = store.load(roomId)
            // Typing may have started before the saved text came back: that wins.
            if (!saved.isNullOrEmpty() && draft.text.isEmpty()) draft.setTextAndPlaceCursorAtEnd(saved)
            @OptIn(FlowPreview::class)
            snapshotFlow { draft.text.toString() }
                .drop(1)
                .map { draftText() }
                .distinctUntilChanged()
                .debounce(SAVE_DELAY_MS)
                .collect { store.save(roomId, it) }
        }
    }

    private suspend fun keepReply() {
        val modes = modes ?: return
        val saved = store.loadReply(roomId)
        if (saved != null) {
            // The first page of the timeline: the reply comes back if its message is in it.
            val loaded = items.filterNotNull().first()
            val message =
                loaded.firstOrNull {
                    (it as? TimelineItem.Message)?.eventId == saved
                } as? TimelineItem.Message
            if (message != null && modes.current.value == ComposeMode.New) {
                modes.reply(message)
            } else {
                store.saveReply(roomId, null)
            }
        }
        modes.current
            .map { (it as? ComposeMode.Reply)?.message?.eventId }
            .distinctUntilChanged()
            .drop(1)
            .collect { store.saveReply(roomId, it) }
    }

    /** The room is being left: save now, without waiting for the pause. */
    fun flush() = store.save(roomId, draftText())

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
