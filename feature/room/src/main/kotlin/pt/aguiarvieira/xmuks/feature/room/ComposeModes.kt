package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** Whether the next send is a new message, a reply, or an edit (which fills the draft). */
class ComposeModes(
    private val draft: TextFieldState,
) {
    internal val mode = MutableStateFlow<ComposeMode>(ComposeMode.New)
    val current: StateFlow<ComposeMode> = mode

    fun reply(message: TimelineItem.Message) {
        if (mode.value is ComposeMode.Edit) draft.clearText()
        mode.value = ComposeMode.Reply(message)
    }

    /** Puts one of our messages back in the composer to be edited. */
    fun edit(message: TimelineItem.Message) {
        val source = message.editSource ?: return
        mode.value = ComposeMode.Edit(message)
        draft.setTextAndPlaceCursorAtEnd(source)
    }

    fun cancel() {
        if (mode.value is ComposeMode.Edit) draft.clearText()
        mode.value = ComposeMode.New
    }
}
