package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse
import pt.aguiarvieira.xmuks.core.protocol.Receipt
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete
import java.util.TreeMap

/** One room's timeline as the UI sees it. */
data class TimelineSnapshot(
    val roomId: String,
    /** Timeline events in order, oldest first. */
    val events: List<Event> = emptyList(),
    /** Every event we hold for the room by rowid — timeline events plus related ones (reply targets, edits). */
    val eventsByRowId: Map<Long, Event> = emptyMap(),
    /** Newest receipt per (user, type, thread), grouped by the event it points at. */
    val receiptsByEventId: Map<String, List<Receipt>> = emptyMap(),
    val hasMoreBefore: Boolean = true,
    val loaded: Boolean = false,
    val loadingOlder: Boolean = false,
)

/** Where pages come from: gomuks' `paginate` (its own database, or the homeserver when it has nothing). */
fun interface Paginator {
    /** Null on failure. [maxTimelineId] 0 = newest page. */
    suspend fun paginate(
        roomId: String,
        maxTimelineId: Long,
        limit: Int,
    ): PaginationResponse?
}

/**
 * Session-only timelines: never persisted. A room is paginated once, then kept current by the
 * sync_complete entries of the live stream — valid only while that stream is continuous:
 * - a catch-up or full sync means events may have been missed → every timeline is dropped (the
 *   open room is reloaded at once, others on their next open);
 * - `reset` for one room drops that room the same way;
 * - resumes (gomuks replays its buffer) keep everything.
 * At most [maxRooms] rooms are kept (least recently opened go first); rooms nobody is watching are
 * trimmed to their newest [keepWhenClosed] events.
 */
class TimelineStore(
    private val paginator: Paginator,
    private val scope: CoroutineScope,
    private val pageSize: Int = PAGE_SIZE,
    private val maxRooms: Int = MAX_ROOMS,
    private val keepWhenClosed: Int = KEEP_WHEN_CLOSED,
) : AccountScoped {
    private class RoomTimeline(
        roomId: String,
    ) {
        val state = MutableStateFlow(TimelineSnapshot(roomId))

        /** timeline rowid → event rowid. */
        val rows = TreeMap<Long, Long>()
        val events = HashMap<Long, Event>()

        /** (user, type, thread) → receipt. */
        val receipts = HashMap<Triple<String, String, String>, Receipt>()
        var hasMoreBefore = true
        var loaded = false
        var loadingOlder = false

        fun publish() {
            state.value =
                state.value.copy(
                    events = rows.values.mapNotNull(events::get),
                    eventsByRowId = HashMap(events),
                    receiptsByEventId = receipts.values.groupBy { it.eventId },
                    hasMoreBefore = hasMoreBefore,
                    loaded = loaded,
                    loadingOlder = loadingOlder,
                )
        }
    }

    private val mutex = Mutex()

    /** Insertion order = recency (accessOrder), for least-recently-opened eviction. */
    private val rooms = LinkedHashMap<String, RoomTimeline>(16, 0.75f, true)

    /** The live snapshot for [roomId]; call [open] to (re)load it. */
    suspend fun observe(roomId: String): StateFlow<TimelineSnapshot> =
        mutex.withLock { room(roomId).state.asStateFlow() }

    /** Makes [roomId] current: served from memory if still valid, otherwise its newest page is fetched. */
    suspend fun open(roomId: String) {
        val needsLoad = mutex.withLock { !room(roomId).loaded.also { evict(keep = roomId) } }
        if (needsLoad) loadNewest(roomId)
    }

    suspend fun loadOlder(roomId: String) {
        val oldest =
            mutex.withLock {
                val room = rooms[roomId] ?: return
                if (!room.canLoadOlder()) return
                room.loadingOlder = true
                room.publish()
                room.rows.firstKey()
            }
        val page = paginator.paginate(roomId, oldest, pageSize)
        mutex.withLock {
            val room = rooms[roomId] ?: return
            room.loadingOlder = false
            if (page != null) {
                room.addPage(page)
                room.hasMoreBefore = page.hasMore
            }
            room.publish()
        }
    }

    /** Feeds the live stream in. Call for every frame, after the database has applied it. */
    suspend fun onFrame(frame: GomuksFrame) {
        when (val event = frame.event) {
            is GomuksEvent.Sync -> {
                applySync(event.sync)
            }

            is GomuksEvent.EventsDecrypted -> {
                mutex.withLock {
                    val room = rooms[event.roomId] ?: return
                    event.events.forEach { room.events[it.rowId] = it }
                    room.publish()
                }
            }

            else -> {
                return
            }
        }
    }

    override suspend fun clearAccountData() = mutex.withLock { rooms.clear() }

    // --- internals --------------------------------------------------------------------------------

    private fun room(roomId: String) = rooms.getOrPut(roomId) { RoomTimeline(roomId) }

    private suspend fun loadNewest(roomId: String) {
        val page = paginator.paginate(roomId, 0, pageSize) ?: return
        mutex.withLock {
            val room = rooms[roomId] ?: return
            // A fresh newest page replaces whatever was there: never merged into something stale.
            room.rows.clear()
            room.events.clear()
            room.receipts.clear()
            room.addPage(page)
            room.hasMoreBefore = page.hasMore
            room.loaded = true
            room.publish()
        }
    }

    private suspend fun applySync(sync: SyncComplete) {
        val reload = mutableListOf<String>()
        mutex.withLock {
            if (sync.clearState || sync.catchup) {
                // Events may have been missed: nothing held is trustworthy any more.
                rooms.values.forEach { it.invalidate()?.let(reload::add) }
            }
            sync.rooms.forEach { (roomId, syncRoom) ->
                val room = rooms[roomId]?.takeIf { it.loaded } ?: return@forEach
                if (syncRoom.reset) {
                    room.invalidate()?.let(reload::add)
                    return@forEach
                }
                syncRoom.events.forEach { room.events[it.rowId] = it }
                syncRoom.timeline.forEach { room.rows[it.timelineRowId] = it.eventRowId }
                room.addReceipts(syncRoom.receipts.values.flatten())
                room.publish()
            }
            sync.leftRooms.forEach { rooms.remove(it) }
        }
        reload.forEach { scope.launch { loadNewest(it) } }
    }

    private fun RoomTimeline.canLoadOlder() = loaded && !loadingOlder && hasMoreBefore && rows.isNotEmpty()

    /** Drops the room's contents; returns its ID if someone is looking at it (so it reloads now). */
    private fun RoomTimeline.invalidate(): String? {
        rows.clear()
        events.clear()
        receipts.clear()
        loaded = false
        hasMoreBefore = true
        publish()
        return state.value.roomId.takeIf { state.subscriptionCount.value > 0 }
    }

    private fun RoomTimeline.addPage(page: PaginationResponse) {
        (page.events + page.relatedEvents).forEach { events[it.rowId] = it }
        page.events.forEach { if (it.timelineRowId != 0L) rows[it.timelineRowId] = it.rowId }
        addReceipts(page.receipts.values.flatten())
    }

    private fun RoomTimeline.addReceipts(list: List<Receipt>) {
        list.forEach { r ->
            val key = Triple(r.userId, r.receiptType, r.threadId.orEmpty())
            val current = receipts[key]
            if (current == null || r.timestamp >= current.timestamp) receipts[key] = r
        }
    }

    /** Keeps at most [maxRooms]; trims rooms nobody watches. [keep] is never evicted. */
    private fun evict(keep: String) {
        rooms.forEach { (id, room) ->
            if (id != keep && room.state.subscriptionCount.value == 0 && room.rows.size > keepWhenClosed) {
                while (room.rows.size > keepWhenClosed) room.rows.pollFirstEntry()
                // Free the events too; a reply target that falls out is fetched again when needed.
                val kept = room.rows.values.toHashSet()
                room.events.keys.retainAll(kept)
                room.hasMoreBefore = true
                room.publish()
            }
        }
        val iterator = rooms.entries.iterator()
        while (rooms.size > maxRooms && iterator.hasNext()) {
            val (id, room) = iterator.next()
            if (id != keep && room.state.subscriptionCount.value == 0) iterator.remove()
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_ROOMS = 10
        const val KEEP_WHEN_CLOSED = 100
    }
}
