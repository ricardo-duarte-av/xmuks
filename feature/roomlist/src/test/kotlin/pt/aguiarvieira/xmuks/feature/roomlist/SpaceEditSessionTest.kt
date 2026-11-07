package pt.aguiarvieira.xmuks.feature.roomlist

import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceEditSessionTest {
    private val start = SpaceEditSession("!space", initial = setOf("!a", "!b"), selected = setOf("!a", "!b"))

    @Test
    fun togglingTracksAddsAndRemoves() {
        val edited =
            start
                .toggle("!b")
                .toggle("!c")
                .toggle("!d")
                .toggle("!d")
        assertEquals(setOf("!c"), edited.added)
        assertEquals(setOf("!b"), edited.removed)
        assertEquals(2, edited.changes)
        assertEquals(0, edited.toggle("!b").toggle("!c").changes)
    }

    @Test
    fun aPartialSaveLeavesOnlyTheFailuresPending() {
        val edited =
            start
                .toggle("!a")
                .toggle("!b")
                .toggle("!c")
                .toggle("!d")
                .copy(saving = true)
        val after = edited.saved(failed = setOf("!b", "!d"))
        assertEquals(false, after.saving)
        assertEquals(setOf("!d"), after.added)
        assertEquals(setOf("!b"), after.removed)
        assertEquals(setOf("!b", "!c"), after.initial)
    }
}
