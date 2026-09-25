package pt.aguiarvieira.xmuks.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDecoderTest {
    private val lines = javaClass.getResource("/stream.jsonl")!!.readText().lines()
    private val frames = lines.mapNotNull(FrameDecoder::decode)

    @Test
    fun `pings and blank lines produce no frame`() {
        assertNull(FrameDecoder.decode("null"))
        assertNull(FrameDecoder.decode(""))
        assertEquals(lines.count { it.isNotBlank() && it != "null" }, frames.size)
    }

    @Test
    fun `connection handshake`() {
        val run = frames[0].event as GomuksEvent.RunId
        assertEquals("1789575625497552244", run.runId)
        assertEquals(42L, run.listenerId)
        val state = frames[1].event as GomuksEvent.ClientState
        assertTrue(state.isLoggedIn)
        assertEquals("@alice:example.org", state.userId)
        assertEquals("ok", (frames[2].event as GomuksEvent.SyncStatus).type)
        assertEquals("eyJ1c2VybmFtZSI6ImFsaWNlIn0.sig", (frames[3].event as GomuksEvent.ImageAuthToken).token)
    }

    @Test
    fun `initial sync carries spaces, edges and account data`() {
        val sync = (frames[4].event as GomuksEvent.Sync).sync
        assertTrue(sync.clearState)
        assertEquals(1790374225978L, sync.serverTimestamp)
        assertEquals(listOf("!space:example.org"), sync.topLevelSpaces)
        assertTrue(
            sync.rooms
                .getValue("!space:example.org")
                .meta!!
                .isSpace
        )
        val edges = sync.spaceEdges.getValue("!space:example.org")
        assertEquals(listOf("!dev:example.org", "!sub:example.org"), edges.map { it.childId })
        assertTrue(edges[1].suggested)
        assertEquals("m.direct", sync.accountData.getValue("m.direct").type)
    }

    @Test
    fun `room payload decodes meta, events, receipts and notifications`() {
        val sync = (frames[5].event as GomuksEvent.Sync).sync
        assertNull("absent top_level_spaces must stay null, not become empty", sync.topLevelSpaces)
        val room = sync.rooms.getValue("!dev:example.org")
        val meta = room.meta!!
        assertFalse(meta.isSpace)
        assertEquals(3, meta.unreadNotifications)
        assertEquals(listOf("@bob:example.org"), meta.lazyLoadSummary!!.heroes)
        val msg = room.events.single { it.rowId == meta.previewEventRowId }
        assertEquals("m.room.message", msg.effectiveType)
        assertEquals("pushed a fix for the SSE keepalive", msg.localContent!!.previewText)
        assertEquals(1, msg.reactions!!.getValue("mxc://example.org/partyparrot"))
        assertEquals(1135L, room.state.getValue("m.room.member").getValue("@bob:example.org"))
        assertTrue(room.notifications.single().highlight)
        assertEquals(listOf("!gone:example.org"), sync.leftRooms)
        assertEquals("!invite:example.org", sync.invitedRooms.single().roomId)
    }

    @Test
    fun `live events keep their negative request ids`() {
        val live = frames.dropWhile { it.event != GomuksEvent.InitComplete }.drop(1)
        assertEquals(listOf(-51010L, -51011L, -51012L, -51013L, -51014L), live.map { it.requestId })
        val sync = (live[2].event as GomuksEvent.Sync).sync
        assertEquals(
            900L,
            sync.rooms
                .getValue("!dev:example.org")
                .timeline
                .single()
                .timelineRowId
        )
        assertTrue(sync.rooms.getValue("!dev:example.org").dismissNotifications)
    }

    @Test
    fun `unknown commands are kept, reordered keys still decode`() {
        val unknown = frames.single { it.command == "some_future_command" }.event
        assertTrue(unknown is GomuksEvent.Unknown)
        val typing = frames.last().event as GomuksEvent.Typing
        assertEquals("!dev:example.org", typing.roomId)
        assertEquals(-51014L, frames.last().requestId)
    }
}
