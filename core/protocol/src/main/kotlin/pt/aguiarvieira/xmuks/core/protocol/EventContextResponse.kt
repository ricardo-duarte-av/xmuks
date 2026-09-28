package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `get_event_context`: the homeserver's `/context` around one event, processed (decrypted) by
 * gomuks but not part of its timeline — [before] is newest first, [after] oldest first.
 */
@Serializable
data class EventContextResponse(
    val event: Event? = null,
    val before: List<Event> = emptyList(),
    val after: List<Event> = emptyList(),
    @SerialName("related_events") val relatedEvents: List<Event> = emptyList(),
)
