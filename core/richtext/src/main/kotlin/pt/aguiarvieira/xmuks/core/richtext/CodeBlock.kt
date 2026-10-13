package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * A code block in a message: a size smaller than the text around it, in gomuks' highlighting
 * colours, its first lines only. Long lines scroll sideways unless `code_block_line_wrap` is on.
 * Tapping opens the whole of it in [CodeViewer]; holding is the message's menu, as elsewhere.
 */
@Composable
internal fun CodeBlock(
    block: HtmlBlock.Code,
    color: Color,
    style: TextStyle,
) {
    val options = LocalRichTextOptions.current
    val theme = options.codeTheme
    val wrap = options.wrapCode
    var viewing by remember { mutableStateOf(false) }
    val text = remember(block, theme) { highlighted(block, theme) }
    val lines = remember(block.code) { block.code.count { it == '\n' } + 1 }
    val codeStyle = codeStyle(style)
    val foreground = theme?.foreground ?: color
    val sideways = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme?.background ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = { viewing = true }, onLongClick = LocalMessageHold.current)
            .padding(8.dp),
    ) {
        Text(
            text,
            style = codeStyle,
            color = foreground,
            softWrap = wrap,
            maxLines = PREVIEW_LINES,
            overflow = TextOverflow.Clip,
            modifier = if (wrap) Modifier else Modifier.horizontalScroll(sideways),
        )
        if (lines > PREVIEW_LINES) {
            Text(
                pluralStringResource(R.plurals.code_more_lines, lines - PREVIEW_LINES, lines - PREVIEW_LINES),
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = MORE_ALPHA),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (viewing) CodeViewer(block, text, codeStyle, theme, onDismiss = { viewing = false })
}

/** Code's type: monospace, a size smaller than [style] (wide lines wrap less, or scroll less). */
internal fun codeStyle(style: TextStyle) =
    style.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = style.fontSize.scaled(CODE_SCALE),
        lineHeight = style.lineHeight.scaled(CODE_SCALE),
    )

private fun TextUnit.scaled(factor: Float) = if (isSpecified) this * factor else this

/** The code with its tokens styled by [theme]; plain without one. */
internal fun highlighted(
    block: HtmlBlock.Code,
    theme: CodeTheme?,
): AnnotatedString =
    buildAnnotatedString {
        append(block.code)
        if (theme != null) {
            block.tokens.forEach { token -> theme.style(token.cls)?.let { addStyle(it, token.start, token.end) } }
        }
    }

private const val PREVIEW_LINES = 15
private const val CODE_SCALE = 0.85f
private const val MORE_ALPHA = 0.7f
