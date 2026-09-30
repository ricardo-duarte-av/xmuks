package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * One thread (MSC3440): its root, then its messages oldest first. Pages come from the homeserver
 * (`paginate_manual` with the root); what arrives live — ours included — comes from the room's
 * timeline, which gomuks keeps whole (thread messages are in it too).
 */
class ThreadTimeline(
    private val roomId: String,
    val rootId: String,
    private val exec: ExecClient,
    live: Flow<TimelineSnapshot>,
) {
    private val root = MutableStateFlow<Event?>(null)
    private val fetched = MutableStateFlow<List<Event>>(emptyList())
    private val related = MutableStateFlow<List<Event>>(emptyList())
    private val paging = MutableStateFlow(Paging())
    private val lock = Mutex()

    /** Events gomuks hasn't stored carry rowid 0: unique stand-ins so they can be keyed. */
    private var standIn = -1L

    private data class Paging(
        val nextBatch: String? = null,
        val hasMore: Boolean = true,
        val loading: Boolean = false,
        val loaded: Boolean = false,
    )

    val snapshot: Flow<TimelineSnapshot> =
        combine(root, fetched, related, paging, live) { root, fetched, related, paging, live ->
            val fromLive = live.events.filter { it.isIn(rootId) }
            // The same event can come both ways: the live copy is the newer (edits, reactions).
            val messages =
                (fetched + fromLive)
                    .associateBy { it.eventId.ifEmpty { "local:${it.rowId}" } }
                    .values
                    .sortedBy { it.timestamp }
            val events = listOfNotNull(root) + messages.filter { it.eventId != rootId }
            TimelineSnapshot(
                roomId = roomId,
                events = events,
                eventsByRowId = live.eventsByRowId + (related + events).associateBy { it.rowId },
                receiptsByEventId = live.receiptsByEventId,
                hasMoreBefore = paging.hasMore,
                loaded = paging.loaded && root != null,
                loadingOlder = paging.loading,
            )
        }

    /** The root, and the newest page of the thread. */
    suspend fun open() {
        root.value = getEvent(rootId)
        loadOlder()
    }

    suspend fun loadOlder() =
        lock.withLock {
            val now = paging.value
            if (!now.hasMore || now.loading) return@withLock
            paging.value = now.copy(loading = true)
            val params =
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("thread_root", JsonPrimitive(rootId))
                    now.nextBatch?.let { put("since", JsonPrimitive(it)) }
                    put("direction", JsonPrimitive("b"))
                    put("limit", JsonPrimitive(PAGE))
                }
            val page = (exec.exec("paginate_manual", params, ExecMode.Read) as? ExecResult.Ok)?.data as? JsonObject
            if (page == null) {
                paging.value = now.copy(loading = false, loaded = true)
                return@withLock
            }
            val events = decode(page["events"]).map(::keyed)
            fetched.update { (events + it).distinctBy { e -> e.eventId } }
            related.update { it + decode(page["related_events"]).map(::keyed) }
            val next = (page["next_batch"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
            paging.value = Paging(nextBatch = next, hasMore = next != null, loading = false, loaded = true)
        }

    private fun keyed(event: Event) = if (event.rowId != 0L) event else event.copy(rowId = standIn--)

    private suspend fun getEvent(eventId: String): Event? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
            }
        val result = exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        return runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), result.data) }.getOrNull()
    }

    private fun decode(json: kotlinx.serialization.json.JsonElement?): List<Event> =
        json
            ?.let {
                runCatching { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), it) }.getOrNull()
            }.orEmpty()

    private companion object {
        const val PAGE = 50
    }
}

/** Whether this event is a message in the thread rooted at [rootId]. */
internal fun Event.isIn(rootId: String) = relationType == THREAD && relatesTo == rootId

internal const val THREAD = "m.thread"
