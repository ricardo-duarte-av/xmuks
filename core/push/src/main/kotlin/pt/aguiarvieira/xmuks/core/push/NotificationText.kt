package pt.aguiarvieira.xmuks.core.push

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.core.text.HtmlCompat

/** HTML as notification text: only the styles notifications draw; images as their alt text. */
internal fun styledHtml(html: String): CharSequence {
    val withAlts = IMG.replace(html) { m -> ALT.find(m.value)?.groupValues?.get(1) ?: "" }
    // Android's parser only makes <tt> monospace.
    val code = CODE.replace(withAlts) { m -> if (m.value.startsWith("</")) "</tt>" else "<tt>" }
    val parsed = HtmlCompat.fromHtml(code, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return keepNotificationSpans(parsed).trim()
}

private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val CODE = Regex("</?code\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ALT = Regex("alt=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

/** A copy of [text] with only bold/italic, strikethrough, underline and monospace spans. */
private fun keepNotificationSpans(text: Spanned): SpannableStringBuilder {
    val out = SpannableStringBuilder(text.toString())
    text.getSpans(0, text.length, Any::class.java).forEach { span ->
        val kept =
            when (span) {
                is StyleSpan -> StyleSpan(span.style)
                is StrikethroughSpan -> StrikethroughSpan()
                is UnderlineSpan -> UnderlineSpan()
                is TypefaceSpan -> TypefaceSpan(span.family ?: "monospace")
                else -> null
            } ?: return@forEach
        out.setSpan(kept, text.getSpanStart(span), text.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}

/**
 * Plain text that may be markdown (bots often send only that): `**bold**`, `*italic*`/`_italic_`,
 * `~~strike~~` and `` `code` `` become styles; `[text](link)` becomes its text.
 */
internal fun styledMarkdown(text: String): CharSequence {
    val out = SpannableStringBuilder()
    var rest = LINK.replace(text) { it.groupValues[1] }
    while (rest.isNotEmpty()) {
        val match = MARKDOWN.find(rest)
        if (match == null) {
            out.append(rest)
            break
        }
        out.append(rest.substring(0, match.range.first))
        val (marker, inner) = match.destructured
        val start = out.length
        out.append(inner)
        out.setSpan(spanFor(marker), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        rest = rest.substring(match.range.last + 1)
    }
    return out
}

private fun spanFor(marker: String): Any =
    when (marker) {
        "**", "__" -> StyleSpan(Typeface.BOLD)
        "~~" -> StrikethroughSpan()
        "`" -> TypefaceSpan("monospace")
        else -> StyleSpan(Typeface.ITALIC)
    }

private val LINK = Regex("\\[([^\\]]+)]\\([^)\\s]+\\)")

// Markers only count outside words: my_long_name stays as it is.
private val MARKDOWN = Regex("(?<![\\w*_~`])(\\*\\*|__|~~|`|\\*|_)(\\S(?:.*?\\S)?)\\1(?![\\w*_~`])")
