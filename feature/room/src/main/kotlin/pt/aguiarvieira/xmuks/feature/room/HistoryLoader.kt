package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSession
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** Loads a message's edit history, or a deleted message's content, for the history sheet. */
internal class HistoryLoader(
    private val scope: CoroutineScope,
    private val session: RoomSession,
) {
    private val _shown = MutableStateFlow<HistoryView?>(null)
    val shown: StateFlow<HistoryView?> = _shown

    fun show(message: TimelineItem.Message) {
        val deleted = message.content == MessageContent.Redacted
        _shown.value = HistoryView(deleted, versions = null)
        scope.launch {
            val versions =
                if (deleted) {
                    listOfNotNull(session.deletedContent(message.eventId))
                } else {
                    session.editHistory(message.eventId).orEmpty()
                }
            // Still wanted (not dismissed while loading)?
            if (_shown.value != null) _shown.value = HistoryView(deleted, versions)
        }
    }

    fun hide() {
        _shown.value = null
    }
}
