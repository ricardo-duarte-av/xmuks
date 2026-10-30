package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import pt.aguiarvieira.xmuks.core.richtext.HtmlContent
import pt.aguiarvieira.xmuks.core.richtext.PlainContent

/** A media message's caption, below the picture or the file. */
@Composable
internal fun Caption(
    caption: String,
    /** Its HTML, when the sender formatted it. */
    html: String?,
    color: Color,
    resolver: MediaResolver,
) {
    val style = MaterialTheme.typography.bodyLarge
    if (html != null) {
        HtmlContent(html, color, style, { resolver.media(it, false) })
    } else {
        PlainContent(caption, color, style)
    }
}
