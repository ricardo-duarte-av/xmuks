package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * Whether the next send is a new message, a reply, or an edit. An edit fills the draft with the
 * message; whatever was being written is set aside meanwhile and comes back when the edit ends.
 */
class ComposeModes(
    private val draft: TextFieldState,
) {
    internal val mode = MutableStateFlow<ComposeMode>(ComposeMode.New)
    val current: StateFlow<ComposeMode> = mode

    /** The draft from before the edit started. */
    private var setAside: String? = null

    /** The room's own draft: what's typed, or while editing, what was set aside. */
    val draftText: String get() = setAside ?: draft.text.toString()

    fun reply(message: TimelineItem.Message) {
        endEdit()
        mode.value = ComposeMode.Reply(message)
    }

    /** Puts one of our messages back in the composer to be edited. */
    fun edit(message: TimelineItem.Message) {
        val source = message.editSource ?: return
        if (mode.value !is ComposeMode.Edit) setAside = draft.text.toString()
        mode.value = ComposeMode.Edit(message)
        draft.setTextAndPlaceCursorAtEnd(source)
    }

    fun cancel() {
        endEdit()
        mode.value = ComposeMode.New
    }

    /** The draft went out: an empty composer, or the set-aside draft after an edit. */
    fun sent() {
        draft.clearText()
        endEdit()
        mode.value = ComposeMode.New
    }

    private fun endEdit() {
        if (mode.value !is ComposeMode.Edit) return
        val previous = setAside.orEmpty()
        setAside = null
        if (previous.isEmpty()) draft.clearText() else draft.setTextAndPlaceCursorAtEnd(previous)
    }
}
