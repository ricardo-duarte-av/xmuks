package pt.aguiarvieira.xmuks.feature.roomlist

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel

class UnreadLevelTest {
    @Test fun `quiet unread is a dot`() = assertEquals(UnreadLevel.Dot to 0, Unread(messages = 5).level())

    @Test fun `with the count shown, quiet unread is its message count`() = assertEquals(UnreadLevel.Count to 5, Unread(messages = 5).level(showCount = true))

    @Test fun `marked unread with no messages stays a dot`() = assertEquals(UnreadLevel.Dot to 0, Unread(marked = true).level(showCount = true))

    @Test fun `notifications and mentions keep their count`() {
        assertEquals(UnreadLevel.Count to 2, Unread(messages = 5, notifications = 2).level(showCount = true))
        assertEquals(UnreadLevel.Mention to 2, Unread(messages = 5, notifications = 2, highlights = 1).level())
    }
}
