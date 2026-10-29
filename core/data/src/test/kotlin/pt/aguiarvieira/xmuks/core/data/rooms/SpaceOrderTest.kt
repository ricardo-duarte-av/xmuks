package pt.aguiarvieira.xmuks.core.data.rooms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceOrderTest {
    private fun assertBetween(
        low: String?,
        high: String?,
    ) {
        val mid = SpaceOrder.between(low, high)!!
        assertTrue("$low < $mid", low == null || low < mid)
        assertTrue("$mid < $high", high == null || mid < high)
        assertTrue(mid.all { it.code in 0x21..0x7E })
    }

    @Test
    fun betweenFindsRoomWhereThereIsSome() {
        assertBetween(null, null)
        assertBetween("a", "b")
        assertBetween("a", "c")
        assertBetween("a~", "b")
        assertBetween(null, "!5")
        assertBetween("a", null)
        assertBetween("~~", null)
        assertBetween("abc", "abd")
    }

    @Test
    fun betweenGivesUpWhenNothingFits() {
        assertNull(SpaceOrder.between(null, "!"))
        assertNull(SpaceOrder.between("b", "a"))
        assertNull(SpaceOrder.between("a", "a"))
        // Only a space would fit, and we never write one.
        assertNull(SpaceOrder.between("a b", "a!"))
    }

    @Test
    fun spreadIsIncreasingAndShort() {
        listOf(1, 2, 20, 500).forEach { n ->
            val keys = SpaceOrder.spread(n)
            assertEquals(n, keys.size)
            assertTrue(keys.zipWithNext().all { (a, b) -> a < b })
            assertTrue(keys.all { it.length == 2 && it.all { c -> c.code in 0x21..0x7E } })
        }
    }

    @Test
    fun movingBetweenOrderedNeighboursWritesOnlyTheMovedSpace() {
        val current = mapOf("a" to "A", "b" to "B", "c" to "C")
        val writes = SpaceOrder.writes(listOf("a", "c", "b"), current, moved = "c")
        assertEquals(setOf("c"), writes.keys)
        assertTrue(writes.getValue("c") in "A".."B" && writes.getValue("c") != "A")
    }

    @Test
    fun movingToEitherEndWritesOnlyTheMovedSpace() {
        val current = mapOf("a" to "A", "b" to "B", "c" to "C")
        assertEquals(setOf("c"), SpaceOrder.writes(listOf("c", "a", "b"), current, "c").keys)
        assertTrue(SpaceOrder.writes(listOf("c", "a", "b"), current, "c").getValue("c") < "A")
        assertTrue(SpaceOrder.writes(listOf("b", "c", "a"), current, "a").getValue("a") > "C")
    }

    @Test
    fun unorderedSpacesGetRenumbered() {
        val current = mapOf("a" to "A", "b" to null, "c" to null)
        val ids = listOf("c", "a", "b")
        val writes = SpaceOrder.writes(ids, current, "c")
        assertEquals(ids.toSet(), writes.keys)
        assertEquals(ids, ids.sortedBy { writes.getValue(it) })
    }
}
