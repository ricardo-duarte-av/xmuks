package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceHierarchyRepository
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class SpaceHierarchyTest {
    @Test
    fun `a page lists the children, not the space, with the links from the space's own entry`() {
        val json =
            GomuksJson
                .parseToJsonElement(
                    """{"rooms": [
                        {"room_id": "!space:x", "name": "Space", "room_type": "m.space", "num_joined_members": 9,
                         "children_state": [
                            {"type": "m.space.child", "state_key": "!a:x", "content": {"via": ["x", "y"], "suggested": true}},
                            {"type": "m.space.child", "state_key": "!b:x", "content": {"via": ["x"]}},
                            {"type": "m.space.child", "state_key": "!gone:x", "content": {}}
                         ]},
                        {"room_id": "!a:x", "name": "Alpha", "num_joined_members": 3, "join_rule": "invite"},
                        {"room_id": "!b:x", "name": "Sub", "room_type": "m.space",
                         "children_state": [
                            {"type": "m.space.child", "state_key": "!a:x", "content": {"via": ["z"]}}
                         ]}
                    ], "next_batch": "p2"}""",
                ).jsonObject
        val page = SpaceHierarchyRepository.parse("!space:x", json)
        assertEquals(listOf("!a:x", "!b:x"), page.rooms.map { it.roomId })
        assertEquals("invite", page.rooms.first().joinRule)
        assertTrue(page.rooms.last().isSpace)
        assertEquals(setOf("!a:x", "!b:x"), page.links.keys)
        assertEquals(listOf("x", "y"), page.links.getValue("!a:x").via)
        assertTrue(page.links.getValue("!a:x").suggested)
        assertFalse(page.links.getValue("!b:x").suggested)
        assertEquals("p2", page.nextBatch)
    }

    @Test
    fun `a later page has no links and the last no next batch`() {
        val json = GomuksJson.parseToJsonElement("""{"rooms": [{"room_id": "!c:x"}], "next_batch": ""}""").jsonObject
        val page = SpaceHierarchyRepository.parse("!space:x", json)
        assertEquals(listOf("!c:x"), page.rooms.map { it.roomId })
        assertTrue(page.links.isEmpty())
        assertNull(page.nextBatch)
    }

    @Test
    fun `one level down, a page at a time`() {
        val params = SpaceHierarchyRepository.params("!space:x", "p2")
        assertEquals(JsonPrimitive("!space:x"), params["room_id"])
        assertEquals(JsonPrimitive(1), params["max_depth"])
        assertEquals(JsonPrimitive("p2"), params["from"])
        assertNull(SpaceHierarchyRepository.params("!space:x", null)["from"])
    }
}
