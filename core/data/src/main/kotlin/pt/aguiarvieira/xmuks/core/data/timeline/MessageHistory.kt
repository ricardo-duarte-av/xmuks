package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import pt.aguiarvieira.xmuks.core.protocol.Event

/**
 * A message's past as the timeline would have shown it: each version (the original, then every
 * edit), or what a deleted message said. Versions are built by [TimelineItemBuilder] like any
 * timeline message, so pictures, inline images, links and replies look as they did when sent.
 */
class MessageHistory(
    private val roomId: String,
    private val getEvent: suspend (eventId: String, unredact: Boolean) -> Event?,
    private val related: suspend (eventId: String, relationType: String) -> List<Event>?,
    /** The live timeline: what replies and thread roots are looked up in. */
    private val live: Flow<TimelineSnapshot>,
    private val items: suspend (TimelineSnapshot) -> List<TimelineItem>,
) {
    /**
     * Every version of [eventId], oldest first: the original, then each edit gomuks has
     * (`get_related_events`, m.replace). A [deleted] message is asked for unredacted (allowed for
     * room moderators). Null when gomuks can't answer; empty when a deletion left nothing to show.
     */
    suspend fun versions(
        eventId: String,
        deleted: Boolean,
    ): List<MessageVersion>? {
        val original = getEvent(eventId, deleted) ?: return null
        if (original.effectiveContent.isEmpty()) return emptyList()
        // Only edits by the original's sender count (anyone can send an m.replace; it's ignored).
        val edits =
            related(eventId, "m.replace")
                .orEmpty()
                .filter { it.sender == original.sender && it.effectiveContent.obj("m.new_content") != null }
                .sortedBy { it.timestamp }
        // Each version is the original as it stood after that edit: as the timeline folds the
        // newest edit in, here each one in turn. Stand-in rowids keep them apart.
        val base = original.copy(redactedBy = null, reactions = null, lastEditRowId = null)
        var standIn = -1L
        val shown = ArrayList<Pair<Event, Event?>>()
        shown += base.copy(rowId = standIn--) to null
        val editRows =
            edits.map { edit ->
                val row = edit.copy(rowId = standIn--, redactedBy = null)
                shown += base.copy(rowId = standIn--, lastEditRowId = row.rowId, timestamp = edit.timestamp) to edit
                row
            }
        val context = live.first().eventsByRowId
        val versionEvents = shown.map { it.first }
        val snapshot =
            TimelineSnapshot(
                roomId,
                events = versionEvents,
                eventsByRowId = context + (versionEvents + editRows).associateBy { it.rowId },
                loaded = true,
            )
        val built = items(snapshot).filterIsInstance<TimelineItem.Message>().associateBy { it.key }
        return shown.mapNotNull { (event, edit) ->
            val message = built["e:${event.rowId}"] ?: return@mapNotNull null
            if (deleted && message.content.isEmpty()) return@mapNotNull null
            MessageVersion(
                timestamp = event.timestamp,
                edit = edit != null,
                message = message.alone(),
            )
        }
    }
}

/** One version of a message: when it was written, and the message as it then read. */
data class MessageVersion(
    val timestamp: Long,
    val edit: Boolean,
    val message: TimelineItem.Message,
)

/** Shown on its own: a whole group of one, nothing that belongs to the live message. */
private fun TimelineItem.Message.alone() =
    copy(
        firstInGroup = true,
        lastInGroup = true,
        edited = false,
        reactions = emptyList(),
        readBy = emptyList(),
        editSource = null,
        threadReplies = 0,
        bridgeDelivery = null,
    )

/** What a deletion leaves: no text, nothing to render. */
private fun MessageContent.isEmpty(): Boolean =
    this == MessageContent.Redacted || (this is MessageContent.Unsupported && body.isNullOrBlank())
