package pt.aguiarvieira.xmuks.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.designsystem.component.avatarColors
import pt.aguiarvieira.xmuks.core.designsystem.component.initialsOf

class InitialsTest {
    @Test
    fun `takes first letter of the first two words`() = assertEquals("MH", initialsOf("Matrix HQ"))

    @Test
    fun `skips matrix sigils`() {
        assertEquals("T", initialsOf("@tulir:maunium.net").take(1))
        assertEquals("G", initialsOf("#gomuks"))
    }

    @Test
    fun `keeps surrogate pairs whole`() = assertEquals("𝐀", initialsOf("𝐀bc"))

    @Test
    fun `empty when nothing printable`() = assertEquals("", initialsOf("  !! "))

    @Test
    fun `avatar colour is stable per id and differs by theme`() {
        assertEquals(avatarColors("!a:b", dark = false), avatarColors("!a:b", dark = false))
        assertNotEquals(avatarColors("!a:b", dark = false), avatarColors("!a:b", dark = true))
    }
}
