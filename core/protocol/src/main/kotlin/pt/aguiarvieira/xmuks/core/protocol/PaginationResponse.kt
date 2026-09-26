package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `paginate` response. [events] are timeline events, newest first, each with its
 * `timeline_rowid` filled in (unlike sync_complete, which sends separate tuples). [relatedEvents]
 * are events the page refers to (reply targets, edits) that aren't themselves in this page.
 */
@Serializable
data class PaginationResponse(
    val events: List<Event> = emptyList(),
    /** event ID → receipts for it. */
    val receipts: Map<String, List<Receipt>> = emptyMap(),
    @SerialName("related_events") val relatedEvents: List<Event> = emptyList(),
    /** Whether older events exist before this page. */
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("from_server") val fromServer: Boolean = false,
)
