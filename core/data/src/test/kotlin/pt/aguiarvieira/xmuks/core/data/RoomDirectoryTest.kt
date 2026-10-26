package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryUnsupported
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository.Companion.directoryResult
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class RoomDirectoryTest {
    @Test
    fun `a page parses its rooms, skipping any without an ID`() {
        val json =
            GomuksJson
                .parseToJsonElement(
                    """{"chunk": [
                        {"room_id": "!a:x", "name": "Alpha", "topic": "", "canonical_alias": "#alpha:x",
                         "num_joined_members": 12, "join_rule": "public", "room_type": "m.space"},
                        {"name": "no id"},
                        {"room_id": "!b:x", "num_joined_members": 1}
                    ], "next_batch": "n1", "total_room_count_estimate": 40}""",
                ).jsonObject
        val page = RoomDirectoryRepository.parsePage(json)
        assertEquals(listOf("!a:x", "!b:x"), page.rooms.map { it.roomId })
        val alpha = page.rooms.first()
        assertEquals("Alpha", alpha.name)
        assertNull(alpha.topic)
        assertEquals("#alpha:x", alpha.alias)
        assertEquals(12, alpha.members)
        assertTrue(alpha.isSpace)
        assertFalse(page.rooms.last().isSpace)
        assertEquals("public", page.rooms.last().joinRule)
        assertEquals("n1", page.nextBatch)
        assertEquals(40, page.total)
    }

    @Test
    fun `the last page has no next batch`() {
        val page = RoomDirectoryRepository.parsePage(GomuksJson.parseToJsonElement("""{"chunk": []}""").jsonObject)
        assertTrue(page.rooms.isEmpty())
        assertNull(page.nextBatch)
    }

    @Test
    fun `no search and no spaces filter sends no filter`() {
        val params = RoomDirectoryRepository.pageParams("  ", spaces = false, since = null)
        assertNull(params["filter"])
        assertNull(params["since"])
        assertEquals("30", params["limit"].toString())
    }

    @Test
    fun `a search, spaces and a next page all go in the request`() {
        val params = RoomDirectoryRepository.pageParams(" foo ", spaces = true, since = "n1")
        val filter = params["filter"] as JsonObject
        assertEquals("\"foo\"", filter["generic_search_term"].toString())
        assertEquals("[\"m.space\"]", filter["room_types"].toString())
        assertEquals("\"n1\"", params["since"].toString())
    }

    @Test
    fun `listing is public, unlisting private`() {
        assertEquals("\"public\"", RoomDirectoryRepository.publishParams("!a", true)["visibility"].toString())
        assertEquals("\"private\"", RoomDirectoryRepository.publishParams("!a", false)["visibility"].toString())
    }

    @Test
    fun `an older gomuks' unknown command is unsupported, other errors are not`() {
        val old = ExecResult.CommandError(418, null, "unknown command \"publish_room\"").directoryResult()
        assertTrue(old.exceptionOrNull() is DirectoryUnsupported)
        val forbidden = ExecResult.CommandError(418, null, "M_FORBIDDEN").directoryResult()
        assertFalse(forbidden.exceptionOrNull() is DirectoryUnsupported)
    }
}
