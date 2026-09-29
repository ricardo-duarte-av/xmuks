package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore

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
) {
    init {
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

    /** The room is being left: save now, without waiting for the pause. */
    fun flush() = store.save(roomId, draftText())

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
