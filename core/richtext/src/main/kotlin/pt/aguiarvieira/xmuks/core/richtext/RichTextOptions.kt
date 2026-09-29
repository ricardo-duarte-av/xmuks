package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** How rich text is drawn where it's shown (gomuks' preferences, as they apply in the room). */
@Immutable
data class RichTextOptions(
    /** Long code lines wrap, rather than scroll sideways. */
    val wrapCode: Boolean = false,
    /** Custom emoji and inline images are drawn; if not, their text (alt) stands in. */
    val inlineImages: Boolean = true,
)

val LocalRichTextOptions = staticCompositionLocalOf { RichTextOptions() }
