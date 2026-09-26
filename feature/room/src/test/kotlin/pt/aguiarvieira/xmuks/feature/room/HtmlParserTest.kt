package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {
    private val colors = HtmlColors(Color.Blue, Color.Magenta, Color.LightGray, Color.Gray, Color.DarkGray)
    private val parser = HtmlParser(colors)

    private fun para(html: String) = (parser.parse(html).single() as HtmlBlock.Paragraph).text

    @Test fun `plain text with breaks`() = assertEquals("one\ntwo", para("one<br>two").text)

    @Test
    fun `bold spans and links`() {
        val text = para("""hello <strong>world</strong> see <a href="https://example.org">this</a>""")
        assertEquals("hello world see this", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && text.text.substring(it.start, it.end) == "world" })
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("https://example.org", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `mentions are pills, javascript links are not links`() {
        val text = para("""<a href="matrix:u/bob:x" class="hicli-matrix-uri hicli-matrix-uri-user">Bob</a> <a href="javascript:alert(1)">x</a>""")
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
        assertTrue(text.spanStyles.any { it.item.background == Color.LightGray })
    }

    @Test
    fun `blocks - quote, code, lists, headings`() {
        val blocks = parser.parse("<h2>Title</h2><blockquote><p>quoted</p></blockquote><pre><code>val x = 1\n</code></pre><ol start=\"3\"><li>a</li><li>b</li></ol><p>after</p>")
        assertEquals(2, (blocks[0] as HtmlBlock.Heading).level)
        assertEquals("quoted", ((blocks[1] as HtmlBlock.Quote).blocks.single() as HtmlBlock.Paragraph).text.text)
        assertEquals("val x = 1", (blocks[2] as HtmlBlock.Code).code)
        val list = blocks[3] as HtmlBlock.Bullets
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
        assertEquals("after", (blocks[4] as HtmlBlock.Paragraph).text.text)
    }

    @Test
    fun `custom emoji become inline content`() {
        val text = para("""nice <img src="mxc://x/party" alt=":party:" height="32">""")
        assertEquals("nice :party:", text.text)
        assertTrue(text.getStringAnnotations(0, text.length).any { it.item == "img:mxc://x/party" })
    }

    @Test
    fun `spoilers hide text, colours apply`() {
        val text = para("""<span data-mx-spoiler>secret</span> <font data-mx-color="#ff0000">red</font>""")
        assertTrue(text.spanStyles.any { it.item.background == Color.DarkGray })
        assertTrue(text.spanStyles.any { it.item.color == Color(0xFFFF0000) })
    }

    @Test
    fun `block elements inside inline ones keep their own lines`() {
        val blocks = parser.parse("""<div>Intro.<a href="https://x.org"><h2>Matrix APIs</h2></a>After</div>""")
        val text = (blocks.single() as HtmlBlock.Paragraph).text
        assertEquals("Intro.\nMatrix APIs\nAfter", text.text)
    }

    @Test
    fun `tables and details flatten into paragraphs`() {
        val blocks = parser.parse("<table><tr><td>a</td><td>b</td></tr></table><details><summary>more</summary>hidden</details>")
        assertEquals(listOf("a", "b", "more", "hidden"), blocks.map { (it as HtmlBlock.Paragraph).text.text })
    }
}
