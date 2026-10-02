package pt.aguiarvieira.xmuks.core.notify

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationContentTest {
    private fun styles(text: CharSequence): List<Pair<String, Any>> {
        val s = text as Spanned
        return s.getSpans(0, s.length, Any::class.java).map { s.subSequence(s.getSpanStart(it), s.getSpanEnd(it)).toString() to it }
    }

    @Test
    fun htmlKeepsBoldItalicStrikeAndCodeOnly() {
        val text = styledHtml("<b>bold</b> <i>it</i> <del>gone</del> <code>x()</code> <a href=\"https://a\">link</a> <img alt=\":cat:\" src=\"mxc://s/i\">")
        assertEquals("bold it gone x() link :cat:", text.toString())
        val spans = styles(text)
        assertEquals(Typeface.BOLD, (spans.single { it.first == "bold" }.second as StyleSpan).style)
        assertEquals(Typeface.ITALIC, (spans.single { it.first == "it" }.second as StyleSpan).style)
        assert(spans.any { it.first == "gone" && it.second is StrikethroughSpan })
        assert(spans.any { it.first == "x()" && it.second is TypefaceSpan })
        assert(spans.none { it.first == "link" })
    }

    @Test
    fun markdownBecomesStyles() {
        val text = styledMarkdown("**[repo](https://github.com/x)** pushed *one* ~~old~~ `a.kt`")
        assertEquals("repo pushed one old a.kt", text.toString())
        val spans = styles(text)
        assertEquals(Typeface.BOLD, (spans.single { it.first == "repo" }.second as StyleSpan).style)
        assertEquals(Typeface.ITALIC, (spans.single { it.first == "one" }.second as StyleSpan).style)
        assert(spans.any { it.first == "old" && it.second is StrikethroughSpan })
        assert(spans.any { it.first == "a.kt" && it.second is TypefaceSpan })
    }

    @Test
    fun snakeCaseIsNotItalic() = assertEquals(0, styles(styledMarkdown("call my_long_name now")).size)

    @Test
    fun linkFormsShowTheirText() = assertEquals("a b c", styledMarkdown("[a](https://x.com) [b](<https://y.com/a b>) [c](https://z.com \"Z\")").toString())

    @Test
    fun pushTextAloneHonoursMarkdown() {
        val user = PushUser("@a:s", "A")
        val push = PushMessage(0, "\$e", "!r:s", "Room", sender = user, self = user, text = "see [the docs](https://the-url.com)")
        assertEquals("see the docs", shownOf(push).text.toString())
    }
}
