package pt.aguiarvieira.xmuks.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Paginator
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineStore
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse
import pt.aguiarvieira.xmuks.core.protocol.Receipt
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom
import pt.aguiarvieira.xmuks.core.protocol.TimelineRowTuple

class TimelineStoreTest {
    /** A fake gomuks: room → timeline rowids 1..n (event rowid = timeline rowid + 1000). */
    private class FakeGomuks(
        var newest: Int = 120,
    ) : Paginator {
        val calls = mutableListOf<Pair<String, Long>>()

        override suspend fun paginate(
            roomId: String,
            maxTimelineId: Long,
            limit: Int,
        ): PaginationResponse {
            calls += roomId to maxTimelineId
            val top = if (maxTimelineId == 0L) newest.toLong() else maxTimelineId - 1
            val ids = (top downTo maxOf(1, top - limit + 1)).toList()
            return PaginationResponse(events = ids.map { event(roomId, it) }, hasMore = ids.last() > 1)
        }
    }

    private val gomuks = FakeGomuks()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = TimelineStore(gomuks, scope, pageSize = 50, maxRooms = 3, keepWhenClosed = 20)

    private fun sync(
        vararg rooms: Pair<String, SyncRoom>,
        catchup: Boolean = false,
        clear: Boolean = false,
    ) = GomuksFrame(
        "sync_complete",
        -1,
        GomuksEvent.Sync(SyncComplete(rooms = rooms.toMap(), catchup = catchup, clearState = clear)),
    )

    private fun ids(roomId: String) =
        runBlocking {
            store
                .observe(roomId)
                .value.events
                .map { it.timelineRowId }
        }

    @Test
    fun `opened once, then served from memory while the stream is up`() =
        runBlocking {
            store.streamChanged(true)
            store.open("!a")
            store.open("!a")
            assertEquals(1, gomuks.calls.size)
            assertEquals((71L..120L).toList(), ids("!a"))
        }

    @Test
    fun `live events append, and newer copies replace older ones`() =
        runBlocking {
            store.open("!a")
            val reacted = event("!a", 120).copy(reactions = mapOf("👍" to 2))
            val live =
                SyncRoom(events = listOf(event("!a", 121), reacted), timeline = listOf(TimelineRowTuple(121, 1121)))
            store.onFrame(sync("!a" to live))
            val snap = store.observe("!a").value
            assertEquals(121L, snap.events.last().timelineRowId)
            assertEquals(mapOf("👍" to 2), snap.events[snap.events.size - 2].reactions)
            assertEquals(1, gomuks.calls.size)
        }

    @Test
    fun `older pages are prepended until there are no more`() =
        runBlocking {
            store.open("!a")
            store.loadOlder("!a")
            assertEquals((21L..120L).toList(), ids("!a"))
            store.loadOlder("!a")
            assertEquals((1L..120L).toList(), ids("!a"))
            assertFalse(store.observe("!a").value.hasMoreBefore)
            store.loadOlder("!a")
            assertEquals("nothing older to ask for", 3, gomuks.calls.size)
        }

    @Test
    fun `a catch-up or full sync drops everything, and the open room reloads at once`() =
        runBlocking {
            store.open("!open")
            store.open("!closed")
            // Someone is looking at "!open" (started immediately, before the gap arrives).
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) { store.observe("!open").collect {} }
            gomuks.newest = 130
            store.onFrame(sync(catchup = true))
            withTimeout(2_000) { store.observe("!open").first { it.loaded && it.events.last().timelineRowId == 130L } }
            assertFalse("unwatched rooms wait for their next open", store.observe("!closed").value.loaded)
            store.open("!closed")
            assertEquals(130L, ids("!closed").last())
            watcher.cancel()
        }

    @Test
    fun `a room reset drops only that room`() =
        runBlocking {
            store.open("!a")
            store.open("!b")
            store.onFrame(sync("!a" to SyncRoom(reset = true)))
            assertFalse(store.observe("!a").value.loaded)
            assertTrue(store.observe("!b").value.loaded)
        }

    @Test
    fun `at most maxRooms are kept, closed rooms trimmed`() =
        runBlocking {
            listOf("!1", "!2", "!3", "!4").forEach { store.open(it) }
            assertEquals(20, ids("!3").size)
            assertFalse("least recently opened goes first", store.observe("!1").value.loaded)
            store.open("!1")
            assertEquals("re-opening an evicted room paginates again", 5, gomuks.calls.size)
        }

    @Test
    fun `receipts keep the newest per user`() =
        runBlocking {
            store.open("!a")

            fun receipt(
                event: String,
                ts: Long,
            ) = Receipt(userId = "@b:x", receiptType = "m.read", eventId = event, timestamp = ts)
            store.onFrame(sync("!a" to SyncRoom(receipts = mapOf("\$e110" to listOf(receipt("\$e110", 10))))))
            store.onFrame(sync("!a" to SyncRoom(receipts = mapOf("\$e120" to listOf(receipt("\$e120", 20))))))
            assertEquals(
                setOf("\$e120"),
                store
                    .observe("!a")
                    .value.receiptsByEventId.keys
            )
        }

    private companion object {
        fun event(
            roomId: String,
            timelineRowId: Long,
        ) = Event(
            rowId = timelineRowId + 1000,
            timelineRowId = timelineRowId,
            roomId = roomId,
            eventId = "\$e$timelineRowId",
            sender = "@b:x",
            type = "m.room.message",
        )

        fun event(
            roomId: String,
            timelineRowId: Int,
        ) = event(roomId, timelineRowId.toLong())
    }

    @Test
    fun `after the stream dropped, opening fetches what was missed and joins it on`() =
        runBlocking {
            store.streamChanged(true)
            store.open("!a")
            store.streamChanged(false)
            gomuks.newest = 130
            store.open("!a")
            assertEquals(2, gomuks.calls.size)
            assertEquals((71L..130L).toList(), ids("!a"))
        }

    @Test
    fun `after the stream dropped, a page that doesn't reach back replaces what was held`() =
        runBlocking {
            store.streamChanged(true)
            store.open("!a")
            store.streamChanged(false)
            gomuks.newest = 300
            store.open("!a")
            assertEquals((251L..300L).toList(), ids("!a"))
        }

    @Test
    fun `while the stream is down, every open asks gomuks`() =
        runBlocking {
            store.open("!a")
            store.open("!a")
            assertEquals(2, gomuks.calls.size)
        }

    @Test
    fun `a room still on screen is refreshed on return, others wait to be opened`() =
        runBlocking {
            store.streamChanged(true)
            store.open("!a")
            val watching = scope.launch { store.observe("!a").collect {} }
            delay(50)
            store.open("!b")
            store.streamChanged(false)
            gomuks.newest = 125
            store.refreshWatched()
            assertEquals((71L..125L).toList(), ids("!a"))
            assertEquals((71L..120L).toList(), ids("!b"))
            watching.cancel()
        }
}
