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
            val versions = session.history.versions(message.eventId, deleted).orEmpty()
            // Still wanted (not dismissed while loading)?
            if (_shown.value != null) _shown.value = HistoryView(deleted, versions)
        }
    }

    fun hide() {
        _shown.value = null
    }
}

/** Loads who reacted to a message with what, for the reactions sheet. */
class ReactionsLoader internal constructor(
    private val scope: CoroutineScope,
    private val session: RoomSession,
) {
    private val _shown = MutableStateFlow<ReactionsView?>(null)
    val shown: StateFlow<ReactionsView?> = _shown

    /** Opens on [eventId]'s reactions, [first] (the one held, if any) at the top. */
    fun show(
        eventId: String,
        first: String?,
    ) {
        _shown.value = ReactionsView(eventId, first, groups = null)
        scope.launch {
            val groups = session.reactions(eventId).orEmpty()
            if (_shown.value?.eventId == eventId) _shown.value = ReactionsView(eventId, first, groups)
        }
    }

    fun hide() {
        _shown.value = null
    }
}
