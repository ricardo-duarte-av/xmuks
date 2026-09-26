package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.TabUnreadRow
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.LocalContent
import pt.aguiarvieira.xmuks.core.protocol.Room
import pt.aguiarvieira.xmuks.core.protocol.SpaceEdge
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete
import pt.aguiarvieira.xmuks.core.protocol.SyncRoom

@RunWith(AndroidJUnit4::class)
class RoomListQueriesTest {
    private val db =
        XmuksDatabase.build(
            ApplicationProvider.getApplicationContext(),
            name = null,
            driver = AndroidSQLiteDriver()
        )
    private val ingestor = SyncIngestor(db)
    private val dao = db.roomListDao()

    @After fun close() = db.close()

    private fun room(
        id: String,
        ts: Long = 0,
        space: Boolean = false,
        dm: String? = null,
        unread: Int = 0,
        highlights: Int = 0,
        preview: Long = 0,
    ) = Room(
        roomId = id,
        name = id,
        dmUserId = dm,
        sortingTimestamp = ts,
        unreadMessages = unread,
        unreadNotifications = unread,
        unreadHighlights = highlights,
        previewEventRowId = preview,
        creationContent = if (space) JsonObject(mapOf("type" to JsonPrimitive("m.space"))) else null,
    )

    private fun member(
        room: String,
        rowId: Long,
        user: String,
        name: String,
    ) = Event(
        rowId = rowId,
        roomId = room,
        eventId = "\$m$rowId",
        sender = user,
        type = "m.room.member",
        stateKey = user,
        content = JsonObject(mapOf("displayname" to JsonPrimitive(name), "membership" to JsonPrimitive("join"))),
    )

    private fun apply(sync: SyncComplete) =
        runBlocking {
            ingestor.apply(GomuksFrame("sync_complete", 0, GomuksEvent.Sync(sync)))
        }

    /**
     *  top ─┬─ sub ─┬─ a
     *       │       └─ b
     *       ├─ b            (b reachable twice: counted once)
     *       └─ loop ─ top   (cycle: must terminate)
     */
    private fun spaces() {
        apply(
            SyncComplete(
                clearState = true,
                rooms =
                    mapOf(
                        "!top" to SyncRoom(meta = room("!top", space = true)),
                        "!sub" to SyncRoom(meta = room("!sub", space = true)),
                        "!loop" to SyncRoom(meta = room("!loop", space = true)),
                        "!a" to SyncRoom(meta = room("!a", ts = 3, unread = 2)),
                        "!b" to SyncRoom(meta = room("!b", ts = 5, unread = 1, highlights = 1)),
                        "!c" to SyncRoom(meta = room("!c", ts = 9)),
                        "!dm" to SyncRoom(meta = room("!dm", ts = 7, dm = "@bob:x", unread = 4)),
                    ),
                spaceEdges =
                    mapOf(
                        "!top" to
                            listOf(
                                SpaceEdge(childId = "!sub", order = "a"),
                                SpaceEdge(childId = "!b"),
                                SpaceEdge(childId = "!loop")
                            ),
                        "!sub" to listOf(SpaceEdge(childId = "!a"), SpaceEdge(childId = "!b")),
                        "!loop" to listOf(SpaceEdge(childId = "!top")),
                    ),
                topLevelSpaces = listOf("!top"),
            ),
        )
    }

    @Test
    fun `space rooms are found through subspaces, once each, despite cycles`() {
        spaces()
        val inTop = runBlocking { dao.roomsInSpace("!top").first() }
        assertEquals(listOf("!b", "!a"), inTop.map { it.roomId })
        assertEquals(listOf("!sub", "!loop"), runBlocking { dao.subspaces("!top").first() }.map { it.roomId })
    }

    @Test
    fun `space unread totals count rooms, each once`() {
        spaces()
        val top = runBlocking { dao.topLevelSpaceSummaries().first() }.single()
        assertEquals(2, top.rooms)
        assertEquals("rooms with anything unread", 2, top.unreadRooms)
        assertEquals("rooms needing attention", 2, top.unreadNotifications)
        assertEquals("rooms with a mention", 1, top.unreadHighlights)
    }

    @Test
    fun `tab badges count rooms per tab, spaces through every level once`() {
        spaces()
        val chats = runBlocking { dao.tabUnread(dmsOnly = false).first() }
        assertEquals(TabUnreadRow(unreadRooms = 3, notifyingRooms = 3, mentionRooms = 1), chats)
        assertEquals(TabUnreadRow(1, 1, 0), runBlocking { dao.tabUnread(dmsOnly = true).first() })
        assertEquals(TabUnreadRow(2, 2, 1), runBlocking { dao.spacesTabUnread().first() })
    }

    @Test
    fun `chats include DMs, the DM tab only DMs, newest first`() {
        spaces()
        assertEquals(listOf("!c", "!dm", "!b", "!a"), runBlocking { dao.chats().first() }.map { it.roomId })
        assertEquals(listOf("!dm"), runBlocking { dao.directMessages().first() }.map { it.roomId })
    }

    @Test
    fun `preview carries the sender's current display name`() {
        val msg =
            Event(
                rowId = 10,
                roomId = "!r",
                eventId = "\$msg",
                sender = "@bob:x",
                type = "m.room.message",
                localContent = LocalContent(previewText = "hello"),
            )
        apply(
            SyncComplete(
                clearState = true,
                rooms =
                    mapOf(
                        "!r" to
                            SyncRoom(
                                meta = room("!r", preview = 10),
                                events = listOf(msg, member("!r", 11, "@bob:x", "Bob")),
                                state = mapOf("m.room.member" to mapOf("@bob:x" to 11L)),
                            ),
                    ),
            ),
        )
        val row = runBlocking { dao.roomSummary("!r").first() }!!
        assertEquals("hello", row.previewText)
        assertEquals("Bob", row.previewSenderName)

        apply(
            SyncComplete(
                rooms =
                    mapOf(
                        "!r" to
                            SyncRoom(
                                events = listOf(member("!r", 12, "@bob:x", "Robert")),
                                state = mapOf("m.room.member" to mapOf("@bob:x" to 12L)),
                            ),
                    ),
            ),
        )
        assertEquals("Robert", runBlocking { dao.roomSummary("!r").first() }!!.previewSenderName)
    }
}
