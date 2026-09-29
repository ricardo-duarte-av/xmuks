package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.ui.text.AnnotatedString

/**
 * Writes text the way HTML lays it out: runs of whitespace (source indentation, newlines) collapse
 * to one space and line breaks come only from markup — unless [preserve] (gomuks' linkified plain
 * text, shown `pre-wrap`), where everything is written as is. Tracks what was last written so
 * block edges don't produce doubled spaces or blank lines.
 */
internal class WhitespaceWriter(
    private val preserve: Boolean,
) {
    private var lastSpace = true
    private var lastNewline = true

    /** A new inline run starts: leading whitespace is dropped. */
    fun reset() {
        lastSpace = true
        lastNewline = true
    }

    fun text(
        builder: AnnotatedString.Builder,
        value: String,
    ) {
        if (preserve) {
            builder.append(value)
            value.lastOrNull()?.let {
                lastSpace = it.isWhitespace()
                lastNewline = it == '\n'
            }
            return
        }
        for (c in value) {
            if (c.isWhitespace()) {
                if (!lastSpace) builder.append(' ')
                lastSpace = true
            } else {
                builder.append(c)
                wroteContent()
            }
        }
    }

    /** Text that's never collapsed (inline code, image alt text). */
    fun verbatim(
        builder: AnnotatedString.Builder,
        value: String,
    ) {
        builder.append(value)
        if (value.isNotEmpty()) wroteContent()
    }

    fun newline(builder: AnnotatedString.Builder) {
        builder.append('\n')
        lastSpace = true
        lastNewline = true
    }

    fun lineBreakIfNeeded(builder: AnnotatedString.Builder) {
        if (!lastNewline) newline(builder)
    }

    /** Something visible (a character, an inline image) was written. */
    fun wroteContent() {
        lastSpace = false
        lastNewline = false
    }
}
