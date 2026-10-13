package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeBlockTest {
    private val parser = HtmlParser(HtmlColors(Color.Blue, Color.Magenta, Color.LightGray, Color.Gray, Color.DarkGray))

    private fun code(html: String) = parser.parse(html).single() as HtmlBlock.Code

    @Test
    fun `gomuks' highlighted block keeps its text and token classes`() {
        // As gomuks' Chroma formatter writes it, line numbers already emptied.
        val html =
            """<pre tabindex="0" class="chroma"><code><span class="line"><span class="ln" data-linenum="1"></span>""" +
                """<span class="cl"><span class="kd">val</span><span class="w"> </span><span class="nv">x</span>""" +
                """<span class="w"> </span><span class="o">=</span><span class="w"> </span><span class="mi">1</span>""" +
                "\n</span></span>" +
                """<span class="line"><span class="ln" data-linenum="2"></span><span class="cl"><span class="c1">// done</span>""" +
                "\n</span></span></code></pre>"
        val block = code(html)
        assertEquals("val x = 1\n// done", block.code)
        val tokens = block.tokens.map { block.code.substring(it.start, it.end) to it.cls }
        assertEquals(listOf("val" to "kd", "x" to "nv", "=" to "o", "1" to "mi", "// done" to "c1"), tokens)
        assertNull(block.language)
    }

    @Test
    fun `a plain block keeps its language tag, which isn't a token`() {
        val block = code("""<pre><code class="language-kotlin">fun main() {}</code></pre>""")
        assertEquals("fun main() {}", block.code)
        assertEquals("kotlin", block.language)
        assertEquals(emptyList<CodeToken>(), block.tokens)
    }

    @Test
    fun `chroma css gives the block colours and a style per token`() {
        val css =
            """
            /* Background */ .bg { color: #e6edf3; background-color: #0d1117; }
            /* PreWrapper */ .chroma { color: #e6edf3; background-color: #0d1117; -webkit-text-size-adjust: none; }
            /* LineNumbers targeted by URL anchor */ .chroma .ln:target { color: #e6edf3; background-color: #6e7681 }
            /* LineNumbers */ .chroma .ln { white-space: pre; user-select: none; margin-right: 0.4em; padding: 0 0.4em 0 0.4em;color: #6e7681 }
            /* Line */ .chroma .line { display: flex; }
            /* Keyword */ .chroma .k { color: #ff7b72 }
            /* NameClass */ .chroma .nc { color: #f0883e; font-weight: bold }
            /* GenericDeleted */ .chroma .gd { color: #ffa198; background-color: #490202 }
            /* Short */ .chroma .s { color: #abc }
            """.trimIndent()
        val theme = CodeTheme.parse(css)
        assertEquals(Color(0xFF0D1117), theme.background)
        assertEquals(Color(0xFFE6EDF3), theme.foreground)
        assertEquals(Color(0xFF6E7681), theme.lineNumbers)
        assertEquals(Color(0xFFFF7B72), theme.style("k")?.color)
        assertEquals(FontWeight.Bold, theme.style("nc")?.fontWeight)
        assertEquals(Color(0xFF490202), theme.style("gd")?.background)
        assertEquals(Color(0xFFAABBCC), theme.style("s")?.color)
        assertNull(theme.style("line"))
        assertNull(theme.style("nf"))
    }

    @Test
    fun `auto follows the app's light or dark, anything else is as chosen`() {
        assertEquals("github-dark", CodeTheme.name("auto", dark = true))
        assertEquals("github", CodeTheme.name("auto", dark = false))
        assertEquals("monokai", CodeTheme.name("monokai", dark = false))
    }
}
