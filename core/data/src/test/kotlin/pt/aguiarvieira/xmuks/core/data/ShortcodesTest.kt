package pt.aguiarvieira.xmuks.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.emoji.ShortcodeQuery
import pt.aguiarvieira.xmuks.core.data.emoji.closedShortcodeAt
import pt.aguiarvieira.xmuks.core.data.emoji.matchingShortcode
import pt.aguiarvieira.xmuks.core.data.emoji.shortcodeQueryAt

class ShortcodesTest {
    @Test
    fun atStartAfterSpacesAndAfterEmoji() {
        assertEquals(ShortcodeQuery(0, 3, "me"), shortcodeQueryAt(":me", 3))
        assertEquals(ShortcodeQuery(6, 11, "memo"), shortcodeQueryAt("hello :memo", 11))
        assertEquals(ShortcodeQuery(2, 7, "+1_x"), shortcodeQueryAt("📝:+1_x", 7))
    }

    @Test
    fun notTimesLinksOrTooShort() {
        assertNull(shortcodeQueryAt("at 10:30", 8))
        assertNull(shortcodeQueryAt("https://x", 9))
        assertNull(shortcodeQueryAt("smile :D", 8))
        assertNull(shortcodeQueryAt("hello :", 7))
        assertNull(shortcodeQueryAt("note: me", 8))
    }

    @Test
    fun aClosingColonEndsTheShortcode() {
        assertEquals(ShortcodeQuery(3, 9, "memo"), closedShortcodeAt("hi :memo:", 9))
        assertEquals(ShortcodeQuery(0, 3, "x"), closedShortcodeAt(":x:", 3))
        assertNull(closedShortcodeAt("10:30:", 6))
        assertNull(closedShortcodeAt("::", 2))
        assertNull(closedShortcodeAt("hi :memo", 8))
    }

    @Test
    fun exactThenPrefixThenContaining() {
        val items = listOf("a" to listOf("memorial"), "b" to listOf("notes", "memo"), "c" to listOf("xmemox"))
        val hits = items.matchingShortcode("memo", { it.second })
        assertEquals(listOf("b" to "memo", "a" to "memorial", "c" to "xmemox"), hits.map { it.first.first to it.second })
        assertEquals(emptyList<Any>(), items.matchingShortcode("zzz", { it.second }))
    }
}
