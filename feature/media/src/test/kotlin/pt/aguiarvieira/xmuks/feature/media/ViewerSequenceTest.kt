package pt.aguiarvieira.xmuks.feature.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

class ViewerSequenceTest {
    private fun media(
        eventId: String,
        index: Int? = null,
    ) = ViewerMedia(ViewerMedia.Kind.Image, "https://x/$eventId/$index", roomId = "!r", eventId = eventId, index = index)

    @Test
    fun `the room's media goes oldest first, with the one opened standing for itself`() {
        val opened = media("\$b").copy(sharedKey = "from-the-timeline")
        val list = merged(listOf(media("\$c"), media("\$b"), media("\$a")), opened)!!
        assertEquals(listOf("\$a", "\$b", "\$c"), list.map { it.eventId })
        assertSame(opened, list[1])
    }

    @Test
    fun `a gallery's items are told apart`() {
        val list = merged(listOf(media("\$g", 1), media("\$g", 0)), media("\$g", 0))!!
        assertEquals(listOf(0, 1), list.map { it.index })
    }

    @Test
    fun `alone, or not among them, there's nothing to swipe`() {
        assertNull(merged(listOf(media("\$a")), media("\$a")))
        assertNull(merged(listOf(media("\$a"), media("\$c")), media("\$b")))
    }
}
