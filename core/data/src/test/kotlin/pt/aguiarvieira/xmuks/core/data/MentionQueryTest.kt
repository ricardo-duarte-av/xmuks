package pt.aguiarvieira.xmuks.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.rooms.MentionQuery
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTarget
import pt.aguiarvieira.xmuks.core.data.rooms.matching
import pt.aguiarvieira.xmuks.core.data.rooms.mentionQueryAt

class MentionQueryTest {
    @Test
    fun atStartAndAfterSpaces() {
        assertEquals(MentionQuery(0, 3, false, "an"), mentionQueryAt("@an", 3))
        assertEquals(MentionQuery(6, 10, true, "gom"), mentionQueryAt("hello #gom", 10))
        assertEquals(MentionQuery(6, 7, false, ""), mentionQueryAt("hello @", 7))
    }

    @Test
    fun notInsideWordsOrLinks() {
        assertNull(mentionQueryAt("me@home", 7))
        assertNull(mentionQueryAt("hello", 5))
        assertNull(mentionQueryAt("[Ann](https://matrix.to/#/@ann:x)", 33))
    }

    @Test
    fun namesStartingWithTheQueryComeFirst() {
        val people = listOf(MentionTarget("@zed:x", "Mo Ann", null, false), MentionTarget("@ann:x", "Ann", null, false))
        assertEquals(listOf("@ann:x", "@zed:x"), people.matching("ann").map { it.id })
        assertEquals("[Ann](https://matrix.to/#/@ann:x)", people[1].link)
    }
}
