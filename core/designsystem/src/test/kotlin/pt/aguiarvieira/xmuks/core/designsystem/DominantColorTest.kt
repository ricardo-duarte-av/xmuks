package pt.aguiarvieira.xmuks.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.designsystem.theme.dominantColor

class DominantColorTest {
    @Test
    fun `the most common colour wins over a vivid accent`() {
        // Synapse Admins' duck, as quantized: white, several yellows, a teal laptop.
        val duck =
            mapOf(
                0xFFFFFFFF.toInt() to 4391,
                0xFFFEE32E.toInt() to 913,
                0xFFFEDC2E.toInt() to 314,
                0xFFFED22E.toInt() to 294,
                0xFF36AFBC.toInt() to 400,
            )
        assertEquals(0xFFFEE32E.toInt(), dominantColor(duck))
    }

    @Test
    fun `similar hues count together`() {
        // Three yellows of 300 outweigh one teal of 500.
        val counts =
            mapOf(
                0xFFFEE32E.toInt() to 300,
                0xFFFEDC2E.toInt() to 300,
                0xFFFED22E.toInt() to 300,
                0xFF36AFBC.toInt() to 500,
            )
        assertEquals(0xFFFEE32E.toInt(), dominantColor(counts))
    }

    @Test
    fun `black and white has no colour`() {
        val logo = mapOf(0xFFFFFFFF.toInt() to 5000, 0xFF000000.toInt() to 3000, 0xFF808080.toInt() to 200)
        assertNull(dominantColor(logo))
    }

    @Test
    fun `a speck of colour is not enough`() {
        val mostlyGrey = mapOf(0xFFFFFFFF.toInt() to 9800, 0xFFE53935.toInt() to 200)
        assertNull(dominantColor(mostlyGrey))
    }

    @Test
    fun `nothing at all`() = assertNull(dominantColor(emptyMap()))
}
