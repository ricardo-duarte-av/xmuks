package pt.aguiarvieira.xmuks.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderColorsTest {
    /** Same index as gomuks web's getUserColorIndex, so colours match across clients. */
    @Test
    fun `index is the code-unit sum modulo ten`() {
        assertEquals("@a:b".sumOf { it.code } % 10, SenderColors.index("@a:b"))
        assertEquals(5, SenderColors.index("A")) // 65 % 10
        assertEquals(0, SenderColors.index(""))
    }
}
