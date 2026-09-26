package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterTest {
    private val names = listOf("João Silva", "Matrix HQ", "matrix-spec", "Ärzte", "Gomuks")

    private fun filter(query: String) = runBlocking { flowOf(names).filteredBy(TextFieldState(query)) { it }.first() }

    @Test fun `blank query keeps everything`() = assertEquals(names, filter("  "))

    @Test fun `case-insensitive substring`() = assertEquals(listOf("Matrix HQ", "matrix-spec"), filter("MATRIX"))

    @Test fun `accents are ignored both ways`() {
        assertEquals(listOf("João Silva"), filter("joao"))
        assertEquals(listOf("Ärzte"), filter("arz"))
    }

    @Test fun `no match gives an empty list`() = assertEquals(emptyList<String>(), filter("zzz"))
}
