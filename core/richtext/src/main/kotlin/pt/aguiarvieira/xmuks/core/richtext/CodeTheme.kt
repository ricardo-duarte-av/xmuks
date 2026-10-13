package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * A code block colour theme, as gomuks serves it (`/_gomuks/codeblock/{name}.css`, Chroma's CSS
 * for the `code_block_theme` preference): the block's own colours, and a style per token class.
 */
@Immutable
data class CodeTheme(
    val background: Color?,
    val foreground: Color?,
    val lineNumbers: Color?,
    private val styles: Map<String, SpanStyle>,
) {
    /** The style for a Chroma token class; none for classes the theme leaves plain, as on the web. */
    fun style(cls: String): SpanStyle? = styles[cls]

    companion object {
        /** gomuks' "auto" (or unset): GitHub's, light or dark with the app. */
        fun name(
            preference: String?,
            dark: Boolean,
        ): String =
            when {
                preference != null && preference != AUTO -> preference
                dark -> "github-dark"
                else -> "github"
            }

        /** Reads Chroma's CSS: `.chroma { … }` for the block, `.chroma .<class> { … }` per token. */
        fun parse(css: String): CodeTheme {
            var block: Map<String, String> = emptyMap()
            var lineNumbers: Color? = null
            val styles = mutableMapOf<String, SpanStyle>()
            RULE.findAll(css).forEach { match ->
                val cls = match.groupValues[1]
                val declarations = declarations(match.groupValues[2])
                when {
                    cls.isEmpty() -> block = declarations
                    cls == "ln" -> lineNumbers = color(declarations["color"])
                    else -> spanStyle(declarations)?.let { styles[cls] = it }
                }
            }
            return CodeTheme(color(block["background-color"]), color(block["color"]), lineNumbers, styles)
        }

        private fun declarations(body: String): Map<String, String> =
            body
                .split(';')
                .mapNotNull { part ->
                    val name = part.substringBefore(':', "").trim()
                    val value = part.substringAfter(':', "").trim()
                    if (name.isEmpty() || value.isEmpty()) null else name to value
                }.toMap()

        private fun spanStyle(d: Map<String, String>): SpanStyle? {
            val style =
                SpanStyle(
                    color = color(d["color"]) ?: Color.Unspecified,
                    background = color(d["background-color"]) ?: Color.Unspecified,
                    fontWeight = if (d["font-weight"] == "bold") FontWeight.Bold else null,
                    fontStyle = if (d["font-style"] == "italic") FontStyle.Italic else null,
                    textDecoration = if (d["text-decoration"] == "underline") TextDecoration.Underline else null,
                )
            return style.takeIf { it != SpanStyle() }
        }

        /** `#rgb` or `#rrggbb`; anything else isn't a colour we use. */
        private fun color(value: String?): Color? {
            val hex = value?.removePrefix("#")?.takeIf { value.startsWith("#") } ?: return null
            val full =
                when (hex.length) {
                    SHORT_HEX -> hex.map { "$it$it" }.joinToString("")
                    LONG_HEX -> hex
                    else -> return null
                }
            return full.toLongOrNull(HEX)?.let { Color(OPAQUE or it) }
        }

        private const val AUTO = "auto"

        /** `.chroma { … }` (empty class) or `.chroma .kd { … }`; `:target` and other variants don't match. */
        private val RULE = Regex("""\.chroma(?: \.([a-z0-9]+))? \{([^}]*)\}""")
        private const val SHORT_HEX = 3
        private const val LONG_HEX = 6
        private const val HEX = 16
        private const val OPAQUE = 0xFF000000L
    }
}
