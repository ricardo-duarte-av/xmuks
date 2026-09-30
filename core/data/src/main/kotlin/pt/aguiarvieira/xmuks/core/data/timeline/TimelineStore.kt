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
    /** Shown from memory while its newest page is being fetched (the stream wasn't keeping it current). */
    val refreshing: Boolean = false,
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
 * - resumes (gomuks replays its buffer) keep everything;
 * - while the stream is down (the app in the background, a reconnect under way) nothing reaches
 *   them: each is unconfirmed until opened again, which fetches its newest page from gomuks at
 *   once (gomuks itself is always current) instead of showing what was held.
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

        /**
         * Our own messages gomuks accepted but sync hasn't delivered yet (its local echoes, by
         * rowid): shown after the timeline until their row arrives. Survive reloads — they're not
         * in any page gomuks would serve until then.
         */
        val echoes = LinkedHashMap<Long, Event>()
        var hasMoreBefore = true
        var loaded = false
        var loadingOlder = false

        /** Kept current by the stream since it was loaded; false once the stream has dropped. */
        var confirmed = false

        /** What's held is on show while its newest page is fetched. */
        var refreshing = false

        fun canLoadOlder() = loaded && !loadingOlder && hasMoreBefore && rows.isNotEmpty()

        /** Drops the room's contents; returns its ID if someone is looking at it (so it reloads now). */
        fun invalidate(): String? {
            rows.clear()
            events.clear()
            receipts.clear()
            loaded = false
            hasMoreBefore = true
            publish()
            return state.value.roomId.takeIf { state.subscriptionCount.value > 0 }
        }

        fun addPage(page: PaginationResponse) {
            (page.events + page.relatedEvents).forEach { events[it.rowId] = it }
            page.events.forEach { if (it.timelineRowId != 0L) rows[it.timelineRowId] = it.rowId }
            echoes.keys.removeAll(rows.values.toSet())
            addReceipts(page.receipts.values.flatten())
        }

        fun addReceipts(list: List<Receipt>) {
            list.forEach { r ->
                val key = Triple(r.userId, r.receiptType, r.threadId.orEmpty())
                val current = receipts[key]
                if (current == null || r.timestamp >= current.timestamp) receipts[key] = r
            }
        }

        fun publish() {
            val timeline = rows.values.mapNotNull(events::get)
            val inTimeline = rows.values.toHashSet()
            val waiting = echoes.values.filter { it.rowId !in inTimeline }
            state.value =
                state.value.copy(
                    events = timeline + waiting,
                    eventsByRowId = HashMap(events).apply { waiting.forEach { put(it.rowId, it) } },
                    receiptsByEventId = receipts.values.groupBy { it.eventId },
                    hasMoreBefore = hasMoreBefore,
                    loaded = loaded,
                    loadingOlder = loadingOlder,
                    refreshing = refreshing,
                )
        }
    }

    private val mutex = Mutex()

    private val _typing = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Room → users typing now (ephemeral; replaced wholesale by each typing notification). */
    val typing: StateFlow<Map<String, List<String>>> = _typing.asStateFlow()

    /** The stream is up and continuous: what it delivers keeps the loaded timelines current. */
    private var live = false

    /** Insertion order = recency (accessOrder), for least-recently-opened eviction. */
    private val rooms = LinkedHashMap<String, RoomTimeline>(16, 0.75f, true)

    /** The live snapshot for [roomId]; call [open] to (re)load it. */
    suspend fun observe(roomId: String): StateFlow<TimelineSnapshot> =
        mutex.withLock { room(roomId).state.asStateFlow() }

    /** Makes [roomId] current: served from memory if still valid, otherwise its newest page is fetched. */
    suspend fun open(roomId: String) {
        val needsLoad =
            mutex.withLock {
                val room = room(roomId)
                evict(keep = roomId)
                !room.loaded || !room.confirmed
            }
        if (needsLoad) loadNewest(roomId)
    }

    /**
     * Rooms on screen that the stream stopped keeping current (the app was away long enough for it
     * to close): their newest page now, rather than when the stream is back and has replayed.
     */
    suspend fun refreshWatched() {
        val stale =
            mutex.withLock {
                rooms.values
                    .filter { it.loaded && !it.confirmed && it.state.subscriptionCount.value > 0 }
                    .map { it.state.value.roomId }
            }
        stale.forEach { loadNewest(it) }
    }

    /**
     * The stream came up ([up]) or went down. Down, every timeline held stops being current: each is
     * fetched afresh when next opened.
     */
    suspend fun streamChanged(up: Boolean) =
        mutex.withLock {
            live = up
            if (!up) rooms.values.forEach { it.confirmed = false }
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
                // An empty page can't move the cursor: asking again would loop.
                room.hasMoreBefore = page.hasMore && page.events.isNotEmpty()
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

            is GomuksEvent.SendComplete -> {
                event.event?.let { onSendComplete(it) }
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

    /** gomuks accepted one of our messages: show its local echo until sync delivers the real row. */
    suspend fun addLocalEcho(event: Event) {
        mutex.withLock {
            val room = room(event.roomId)
            room.echoes[event.rowId] = event
            room.publish()
        }
    }

    /** A send finished (sent, or failed with `send_error`): update whichever copy we hold. */
    private suspend fun onSendComplete(event: Event) {
        mutex.withLock {
            val room = rooms[event.roomId] ?: return
            if (room.echoes.containsKey(event.rowId)) room.echoes[event.rowId] = event
            if (room.events.containsKey(event.rowId)) room.events[event.rowId] = event
            room.publish()
        }
    }

    override suspend fun clearAccountData() {
        mutex.withLock { rooms.clear() }
        _typing.value = emptyMap()
    }

    // --- internals --------------------------------------------------------------------------------

    private fun room(roomId: String) = rooms.getOrPut(roomId) { RoomTimeline(roomId) }

    /**
     * A push for [roomId]: if its timeline is held but the stream isn't keeping it current (the app
     * is away), its newest page now, so the room is current by the time the notification is tapped.
     * Rooms not held are left alone: opening them loads them anyway.
     */
    suspend fun prefetch(roomId: String) {
        val stale = mutex.withLock { rooms[roomId]?.let { it.loaded && !it.confirmed } == true }
        if (stale) loadNewest(roomId)
    }

    private suspend fun loadNewest(roomId: String) {
        mutex.withLock {
            rooms[roomId]?.takeIf { it.loaded && !it.refreshing }?.let {
                it.refreshing = true
                it.publish()
            }
        }
        val page = paginator.paginate(roomId, 0, pageSize)
        mutex.withLock {
            val room = rooms[roomId] ?: return
            room.refreshing = false
            if (page == null) {
                room.publish()
                return
            }
            // Onto what's held only if it reaches back into it (no gap between them); otherwise
            // the newest page replaces it, never leaving a hole in the middle.
            val oldest = page.events.mapNotNull { it.timelineRowId.takeIf { id -> id != 0L } }.minOrNull()
            val joins = room.loaded && oldest != null && room.rows.isNotEmpty() && oldest <= room.rows.lastKey()
            if (!joins) {
                room.rows.clear()
                room.events.clear()
                room.receipts.clear()
                room.hasMoreBefore = page.hasMore
            }
            room.addPage(page)
            room.loaded = true
            room.confirmed = live
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
                syncRoom.timeline.forEach {
                    room.rows[it.timelineRowId] = it.eventRowId
                    room.echoes.remove(it.eventRowId) // the real row is in: the echo has served
                }
                room.addReceipts(syncRoom.receipts.values.flatten())
                room.publish()
            }
            sync.leftRooms.forEach { rooms.remove(it) }
        }
        reload.forEach { scope.launch { loadNewest(it) } }
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
