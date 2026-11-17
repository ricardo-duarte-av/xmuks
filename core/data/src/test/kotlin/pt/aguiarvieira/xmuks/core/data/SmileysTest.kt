package pt.aguiarvieira.xmuks.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.emoji.Smiley
import pt.aguiarvieira.xmuks.core.data.emoji.smileyEndingAt

class SmileysTest {
    @Test
    fun aWholeWordSmiley() {
        assertEquals(Smiley(0, 2, "🙂"), smileyEndingAt(":)", 2))
        assertEquals(Smiley(3, 5, "😕"), smileyEndingAt("ok :/ ", 5))
        assertEquals(Smiley(3, 6, "😑"), smileyEndingAt("hm\n-_-", 6))
        assertEquals(Smiley(0, 3, "🙌"), smileyEndingAt("\\o/", 3))
    }

    @Test
    fun notInsideWordsOrLinks() {
        assertNull(smileyEndingAt("http:/", 6))
        assertNull(smileyEndingAt("(:)", 3))
        assertNull(smileyEndingAt("see 8)", 6))
        assertNull(smileyEndingAt("plan B)", 7))
        assertNull(smileyEndingAt("hello", 5))
        assertNull(smileyEndingAt("", 0))
        assertNull(smileyEndingAt(":)", 3))
    }
}
