package pt.aguiarvieira.xmuks.core.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonTokensTest {
    private fun tokens(json: String) = jsonTokens(json).map { json.substring(it.start, it.end) to it.cls }

    @Test
    fun `keys, strings, numbers, literals and punctuation each have their class`() {
        val json = "{\n  \"a\": \"b\",\n  \"n\": -1.5e3,\n  \"ok\": [true, null]\n}"
        assertEquals(
            listOf(
                "{" to "p",
                "\"a\"" to "nt",
                ":" to "p",
                "\"b\"" to "s2",
                "," to "p",
                "\"n\"" to "nt",
                ":" to "p",
                "-1.5e3" to "m",
                "," to "p",
                "\"ok\"" to "nt",
                ":" to "p",
                "[" to "p",
                "true" to "kc",
                "," to "p",
                "null" to "kc",
                "]" to "p",
                "}" to "p",
            ),
            tokens(json),
        )
    }

    @Test
    fun `escaped quotes stay inside their string`() {
        assertEquals(listOf("\"say \\\"hi\\\"\"" to "s2"), tokens("\"say \\\"hi\\\"\""))
    }

    @Test
    fun `what isn't JSON is left plain`() {
        assertEquals(listOf("\"x" to "s2"), tokens("nope \"x"))
    }
}
