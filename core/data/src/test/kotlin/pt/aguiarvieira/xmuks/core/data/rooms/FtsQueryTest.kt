package pt.aguiarvieira.xmuks.core.data.rooms

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {
    @Test
    fun wordsAreQuotedAndTheLastIsAPrefix() = assertEquals("\"hello\" \"wor\"*", ftsQuery("  hello   wor "))

    @Test
    fun quotesAndOperatorsCannotBreakTheQuery() = assertEquals("\"say\" \"\"\"hi\"\"\" \"OR\" \"a-b\"*", ftsQuery("say \"hi\" OR a-b"))

    @Test
    fun blankIsEmpty() = assertEquals("", ftsQuery("   "))
}
