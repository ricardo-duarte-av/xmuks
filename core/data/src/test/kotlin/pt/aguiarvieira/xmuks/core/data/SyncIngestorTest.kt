package pt.aguiarvieira.xmuks.core.data

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.network.ResyncRequired
import pt.aguiarvieira.xmuks.core.protocol.AccountData
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.InvitedRoom
import pt.aguiarvieira.xmuks.core.protocol.Room
import pt.aguiarvieira.xmuks.core.protocol.SpaceEdge
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom
import pt.aguiarvieira.xmuks.core.protocol.TimelineRowTuple

/** The cache must never show what no longer exists. One test per way it could. */
@RunWith(AndroidJUnit4::class)
class SyncIngestorTest {
    private var now = 1_000_000L
    private val db =
        XmuksDatabase.build(
            ApplicationProvider.getApplicationContext(),
            name = null,
            driver = AndroidSQLiteDriver()
        )
    private val ingestor = SyncIngestor(db, clock = { now })
    private val rooms = db.roomListDao()

    @After fun close() = db.close()

    // --- builders ----------------------------------------------------------------------------

    private fun room(
        id: String,
        name: String? = id.removePrefix("!").substringBefore(':'),
        avatar: String? = null,
        space: Boolean = false,
    ) = Room(
        roomId = id,
        name = name,
        avatar = avatar,
        creationContent = if (space) JsonObject(mapOf("type" to JsonPrimitive("m.space"))) else null,
    )

    private fun event(
        room: String,
        rowId: Long,
    ) = Event(rowId = rowId, roomId = room, eventId = "\$e$rowId", sender = "@b:x", type = "m.room.message")

    private fun sync(
        vararg rooms: Pair<String, SyncRoom>,
        clearState: Boolean = false,
        ts: Long = now,
        catchup: Boolean = false,
        left: List<String> = emptyList(),
        edges: Map<String, List<SpaceEdge>> = emptyMap(),
        topLevel: List<String>? = null,
        accountData: Map<String, AccountData> = emptyMap(),
        invites: List<InvitedRoom> = emptyList(),
    ) = frame(
        GomuksEvent.Sync(
            SyncComplete(
                serverTimestamp = ts,
                clearState = clearState,
                catchup = catchup,
                rooms = rooms.toMap(),
                leftRooms = left,
                spaceEdges = edges,
                topLevelSpaces = topLevel,
                accountData = accountData,
                invitedRooms = invites,
            ),
        ),
    )

    private fun frame(
        event: GomuksEvent,
        requestId: Long = 0,
    ) = GomuksFrame("x", requestId, event)

    private fun apply(vararg frames: GomuksFrame) = runBlocking { frames.forEach { ingestor.apply(it) } }

    private fun initialSync(vararg data: Pair<String, SyncRoom>) =
        apply(sync(*data, clearState = true), frame(GomuksEvent.InitComplete))

    private val counts get() = runBlocking { rooms.counts().first() }

    private fun roomRow(id: String) = runBlocking { rooms.room(id).first() }

    private fun load() = runBlocking { ingestor.load() }

    // --- tests -------------------------------------------------------------------------------

    @Test
    fun `metadata is replaced, not merged - a removed avatar or name disappears`() {
        initialSync("!a:x" to SyncRoom(meta = room("!a:x", name = "Old", avatar = "mxc://x/old")))
        apply(sync("!a:x" to SyncRoom(meta = room("!a:x", name = null, avatar = null)), catchup = true))
        val row = roomRow("!a:x")!!
        assertNull(row.name)
        assertNull(row.avatar)
    }

    @Test
    fun `a room left while offline is gone with everything attached to it`() {
        initialSync(
            "!space:x" to SyncRoom(meta = room("!space:x", space = true)),
            "!a:x" to
                SyncRoom(
                    meta = room("!a:x"),
                    events = listOf(event("!a:x", 1)),
                    timeline = listOf(TimelineRowTuple(1, 1))
                ),
        )
        apply(sync(edges = mapOf("!space:x" to listOf(SpaceEdge(childId = "!a:x"))), topLevel = listOf("!space:x")))
        apply(sync(left = listOf("!a:x", "!space:x"), catchup = true, topLevel = emptyList()))
        assertNull(roomRow("!a:x"))
        assertEquals(0, counts.rooms)
        assertEquals(0, counts.spaces)
        assertEquals(0, counts.topLevelSpaces)
        assertTrue(runBlocking { rooms.spaceChildren("!space:x").first() }.isEmpty())
    }

    @Test
    fun `a space's child list is replaced whole - removed children vanish`() {
        initialSync(
            "!space:x" to SyncRoom(meta = room("!space:x", space = true)),
            "!a:x" to SyncRoom(meta = room("!a:x")),
            "!b:x" to SyncRoom(meta = room("!b:x")),
        )
        apply(sync(edges = mapOf("!space:x" to listOf(SpaceEdge(childId = "!a:x"), SpaceEdge(childId = "!b:x")))))
        apply(sync(edges = mapOf("!space:x" to listOf(SpaceEdge(childId = "!b:x")))))
        assertEquals(listOf("!b:x"), runBlocking { rooms.spaceChildren("!space:x").first() }.map { it.roomId })
    }

    @Test
    fun `an interrupted full sync is redone in full, then sweeps what it didn't deliver`() {
        initialSync("!a:x" to SyncRoom(meta = room("!a:x")), "!gone:x" to SyncRoom(meta = room("!gone:x")))
        assertEquals(now, load().lastServerTs)

        now += 1
        apply(sync("!a:x" to SyncRoom(meta = room("!a:x")), clearState = true)) // ...connection drops here
        assertEquals("no catch-up from a half-finished snapshot", ResumePoint(), load())

        apply(sync("!a:x" to SyncRoom(meta = room("!a:x")), clearState = true), frame(GomuksEvent.InitComplete))
        assertNull("rooms missing from the full snapshot are swept", roomRow("!gone:x"))
        assertNotNull(roomRow("!a:x"))
        assertEquals(now, load().lastServerTs)
    }

    @Test
    fun `the sweep keeps cached timelines of rooms that still exist`() {
        initialSync(
            "!a:x" to
                SyncRoom(
                    meta = room("!a:x"),
                    events = listOf(event("!a:x", 7)),
                    timeline = listOf(TimelineRowTuple(1, 7))
                )
        )
        initialSync("!a:x" to SyncRoom(meta = room("!a:x")))
        val kept = runBlocking { db.syncDao().roomExists("!a:x") }
        assertTrue(kept)
        assertEquals(1, runBlocking { timelineRows("!a:x") })
    }

    @Test
    fun `global account data and invites missing from a full sync are swept`() {
        apply(
            sync(
                clearState = true,
                accountData =
                    mapOf(
                        "m.direct" to AccountData(type = "m.direct"),
                        "old.type" to AccountData(type = "old.type")
                    ),
                invites = listOf(InvitedRoom("!inv:x")),
            ),
            frame(GomuksEvent.InitComplete),
        )
        assertEquals(1, counts.invites)
        apply(
            sync(clearState = true, accountData = mapOf("m.direct" to AccountData(type = "m.direct"))),
            frame(GomuksEvent.InitComplete)
        )
        assertEquals(0, counts.invites)
        assertEquals(listOf("m.direct"), runBlocking { globalAccountDataTypes() })
    }

    @Test
    fun `joining a room clears its invite`() {
        initialSync()
        apply(sync(invites = listOf(InvitedRoom("!a:x"))))
        assertEquals(1, counts.invites)
        apply(sync("!a:x" to SyncRoom(meta = room("!a:x"))))
        assertEquals(0, counts.invites)
    }

    @Test
    fun `a different account behind the same backend wipes everything and forces a full sync`() {
        apply(frame(GomuksEvent.ClientState(isLoggedIn = true, userId = "@alice:x", deviceId = "D1")))
        initialSync("!a:x" to SyncRoom(meta = room("!a:x")))
        runBlocking { ingestor.save(ResumePoint("run", -9, 3, now)) }
        try {
            apply(frame(GomuksEvent.ClientState(isLoggedIn = true, userId = "@bob:x", deviceId = "D2")))
            fail("expected ResyncRequired")
        } catch (_: ResyncRequired) {
        }
        assertEquals(0, counts.rooms)
        assertEquals(ResumePoint(), load())
    }

    @Test
    fun `resume point carries the stream position, and a stale full sync forces a new one`() {
        initialSync("!a:x" to SyncRoom(meta = room("!a:x")))
        runBlocking { ingestor.save(ResumePoint("run", -9, 3, 0)) }
        now += 10
        apply(sync(ts = now), frame(GomuksEvent.Typing("!a:x"), requestId = -10))
        assertEquals(ResumePoint("run", -9, 3, now), load())

        now += 25 * 60 * 60 * 1000L
        assertEquals("a day-old snapshot is refreshed in full", ResumePoint(), load())
    }

    @Test
    fun `clearing account data empties the cache`() {
        initialSync("!a:x" to SyncRoom(meta = room("!a:x")))
        runBlocking { ingestor.clearAccountData() }
        assertEquals(0, counts.rooms)
        assertEquals(ResumePoint(), load())
    }

    private suspend fun timelineRows(roomId: String): Int =
        db.useReaderConnection { conn ->
            conn.usePrepared("SELECT COUNT(*) FROM timeline WHERE roomId = ?") {
                it.bindText(1, roomId)
                it.step()
                it.getInt(0)
            }
        }

    private suspend fun globalAccountDataTypes(): List<String> =
        db.useReaderConnection { conn ->
            conn.usePrepared("SELECT type FROM account_data WHERE roomId = '' ORDER BY type") {
                buildList { while (it.step()) add(it.getText(0)) }
            }
        }
}
