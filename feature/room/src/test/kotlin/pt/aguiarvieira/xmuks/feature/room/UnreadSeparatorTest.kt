package pt.aguiarvieira.xmuks.feature.room

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

class UnreadSeparatorTest {
    private fun message(at: Long) =
        TimelineItem.Message(
            key = "m$at",
            eventId = "\$e$at",
            sender = "@a:b",
            label = SenderLabel("@a:b", "A"),
            senderAvatarMxc = null,
            fromMe = false,
            timestamp = at,
            content = MessageContent.Text("hi", null, TextKind.Text, false),
            reply = null,
            reactions = emptyList(),
            edited = false,
            firstInGroup = true,
            lastInGroup = true,
            readBy = emptyList(),
            sendError = null,
        )

    // Newest first, as the timeline holds them.
    private val items = listOf(message(50), message(40), message(30), message(20))

    private fun keys(list: List<TimelineItem>) = list.map { it.key }

    @Test
    fun `goes right after the last read message`() = assertEquals(listOf("m50", "m40", "unread-separator", "m30", "m20"), keys(items.withUnreadSeparator(30)))

    @Test
    fun `a marker between messages (an edit, a reaction) still lands by time`() = assertEquals(listOf("m50", "unread-separator", "m40", "m30", "m20"), keys(items.withUnreadSeparator(45)))

    @Test
    fun `nothing when all is read, the marker isn't loaded, or older than everything`() {
        assertEquals(keys(items), keys(items.withUnreadSeparator(50)))
        assertEquals(keys(items), keys(items.withUnreadSeparator(null)))
        assertEquals(keys(items), keys(items.withUnreadSeparator(10)))
    }
}
