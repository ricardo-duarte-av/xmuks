package pt.aguiarvieira.xmuks.core.richtext

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
    fun `spoilers are marked, colours apply`() {
        val text = para("""<span data-mx-spoiler>secret</span> <font data-mx-color="#ff0000">red</font>""")
        assertEquals("secret", spoilerRanges(text).single().let { text.text.substring(it.start, it.end) })
        assertTrue(text.spanStyles.any { it.item.color == Color(0xFFFF0000) })
    }

    @Test
    fun `a spoiler's reason shows in brackets, gomuks' or the attribute's`() {
        val gomuks = para("""<span class="spoiler-reason">spoilertest</span><span class="hicli-spoiler">cw</span>""")
        assertEquals("(spoilertest) cw", gomuks.text)
        assertEquals("cw", spoilerRanges(gomuks).single().let { gomuks.text.substring(it.start, it.end) })
        assertEquals("(why) cw", para("""<span data-mx-spoiler="why">cw</span>""").text)
    }

    @Test
    fun `hidden spoilers hide text and links, revealed ones keep them, each taps to toggle`() {
        val text = para("""<span class="hicli-spoiler">see <a href="https://x.org">x</a></span> and <span class="hicli-spoiler">two</span>""")
        val spoilers = spoilerRanges(text)
        val tapped = mutableListOf<String>()
        val hidden = withSpoilers(text, spoilers, emptySet()) { tapped += it }
        assertTrue(hidden.getLinkAnnotations(0, hidden.length).none { it.item is LinkAnnotation.Url })
        assertTrue(hidden.spanStyles.any { it.item.color == Color.Transparent && it.start == spoilers[1].start })
        hidden.getLinkAnnotations(0, hidden.length).forEach { (it.item as LinkAnnotation.Clickable).linkInteractionListener?.onClick(it.item) }
        assertEquals(spoilers.map { it.item }, tapped)

        val firstShown = withSpoilers(text, spoilers, setOf(spoilers[0].item)) {}
        assertEquals(
            "https://x.org",
            firstShown
                .getLinkAnnotations(0, firstShown.length)
                .mapNotNull { it.item as? LinkAnnotation.Url }
                .single()
                .url
        )
        assertTrue(firstShown.spanStyles.none { it.item.color == Color.Transparent && it.start == spoilers[0].start })
    }

    @Test
    fun `quoted spoilers stay solid blocks`() {
        val snippet = snippetOf(parser.parse("""a <span class="hicli-spoiler">b</span>"""), colors)
        assertTrue(snippet.spanStyles.any { it.item.background == Color.DarkGray && snippet.text.substring(it.start, it.end) == "b" })
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

    @Test
    fun `html whitespace collapses like a browser, and empty lists vanish`() {
        val html =
            "<strong>[<a href=\"https://x.org\">repo</a>]</strong> <a href=\"https://x.org/u\">user</a>      pushed\n" +
                "        <a href=\"https://x.org/c\">0 commits</a>\n    to\n v1.1.80 (new tag)<ul>  </ul>"
        val blocks = parser.parse(html)
        assertEquals(1, blocks.size)
        assertEquals("[repo] user pushed 0 commits to v1.1.80 (new tag)", (blocks.single() as HtmlBlock.Paragraph).text.text)
    }

    @Test
    fun `plain text keeps its line breaks`() {
        val blocks = HtmlParser(colors, preserveWhitespace = true).parse("line one\n  line two")
        assertEquals("line one\n  line two", (blocks.single() as HtmlBlock.Paragraph).text.text)
    }

    @Test
    fun `gomuks inline images - hidden fallback skipped, media path back to mxc`() {
        val html =
            "<a class=\"hicli-inline-img-fallback\" style=\"display: none;\" href=\"_gomuks/media/x.org/abc?encrypted=false\">:shiggy:</a>" +
                "<img alt=\":shiggy:\" src=\"_gomuks/media/x.org/abc?encrypted=false\" class=\"hicli-custom-emoji\"> custom emoji"
        val text = (parser.parse(html).single() as HtmlBlock.Paragraph).text
        assertEquals(
            "mxc://x.org/abc",
            text
                .getStringAnnotations(0, text.length)
                .single()
                .item
                .removePrefix(HtmlParser.IMAGE_PREFIX)
        )
        assertTrue(!text.text.contains(":shiggy::shiggy:"))
    }

    @Test
    fun `gomuks colours and spoilers - style attribute and class`() {
        val text =
            para(
                """<span class="hicli-spoiler">secret</span> <span style="background-color: #000000;color: #39ff14;">M</span>""",
            )
        assertEquals(1, spoilerRanges(text).size)
        assertTrue(text.spanStyles.any { it.item.color == Color(0xFF39FF14) && it.item.background == Color(0xFF000000) })
    }

    @Test
    fun `sized images stand as blocks, emoji and sizeless ones stay inline`() {
        val html =
            "<img src=\"mxc://a/pic\" class=\"hicli-inline-img hicli-sized-inline-img\" style=\"width: 320.00px; height: 99.00px;\">" +
                "<img src=\"mxc://a/raw\" width=\"640\" height=\"240\" alt=\"raw\">" +
                "<img src=\"mxc://a/emo\" class=\"hicli-inline-img hicli-custom-emoji\">" +
                "<img src=\"mxc://a/none\">"
        val blocks = parser.parse(html)
        assertEquals(HtmlBlock.Picture("mxc://a/pic", 320, 99, "", null), blocks[0])
        assertEquals(HtmlBlock.Picture("mxc://a/raw", 320, 120, "raw", null), blocks[1])
        val text = (blocks[2] as HtmlBlock.Paragraph).text
        assertEquals(listOf("img:mxc://a/emo", "img:mxc://a/none"), text.getStringAnnotations(0, text.length).map { it.item })
    }

    @Test
    fun `a link around only a picture makes a linked picture`() {
        val html =
            "<a href=\"https://vrkknn.net\"> <img src=\"mxc://v/b\" width=\"320\" height=\"120\" alt=\"vrkknn.net\"></a>"
        assertEquals(listOf(HtmlBlock.Picture("mxc://v/b", 320, 120, "vrkknn.net", "https://vrkknn.net")), parser.parse(html))
    }

    @Test
    fun `images from anywhere but Matrix are never loaded - their alt text shows`() {
        val html = """<img src="https://tracker.example/pixel.png" alt="[web]"> <img src="file:///data/x.png" alt="[file]">"""
        val text = para(html)
        assertEquals("[web] [file]", text.text)
        assertTrue(text.getStringAnnotations(0, text.length).isEmpty())
        assertTrue(parser.parse("""<img src="https://x.example/a.png" width="320" height="120">""").none { it is HtmlBlock.Picture })
    }
}
