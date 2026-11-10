package pt.aguiarvieira.xmuks.feature.roomlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryRoom
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryUnsupported
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceHierarchyPage
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceLink
import java.io.IOException

class HierarchyLoadTest {
    private fun room(id: String) = DirectoryRoom(id, id, null, null, null, 1, "public", false)

    private val first =
        SpaceHierarchyPage(
            rooms = listOf(room("!a"), room("!b"), room("!c")),
            links = mapOf("!a" to SpaceLink(listOf("x"), false), "!d" to SpaceLink(listOf("y"), true)),
            nextBatch = "p2",
        )

    @Test
    fun joinedRoomsDropOutAndSuggestedOnesComeFirst() {
        val load =
            HierarchyLoad("!space", loading = true)
                .added(first)
                .added(SpaceHierarchyPage(listOf(room("!d"), room("!a")), emptyMap(), null))
        val shown = load.shown(joined = setOf("!b"))
        assertEquals(listOf("!d", "!a", "!c"), shown.rooms.map { it.room.roomId })
        // The second page's room keeps the first page's link.
        assertEquals(listOf("y"), shown.rooms.first().via)
        assertTrue(shown.rooms.first().suggested)
        assertEquals(emptyList<String>(), shown.rooms.last().via)
        assertFalse(shown.hasMore)
        assertFalse(shown.loading)
    }

    @Test
    fun moreToComeWhileThereIsANextBatch() {
        val shown = HierarchyLoad("!space").added(first).shown(emptySet())
        assertTrue(shown.hasMore)
        assertTrue(shown.visible)
    }

    @Test
    fun aFailureShowsButAnOldGomuksHidesTheSection() {
        val failed = HierarchyLoad("!space", loading = true).failed(IOException("offline")).shown(emptySet())
        assertEquals("offline", failed.error)
        assertTrue(failed.visible)
        val old = HierarchyLoad("!space", loading = true).failed(DirectoryUnsupported()).shown(emptySet())
        assertNull(old.error)
        assertFalse(old.visible)
    }
}
