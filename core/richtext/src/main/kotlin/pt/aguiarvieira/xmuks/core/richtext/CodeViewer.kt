package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A code block on its own, full screen: scrolls both ways (or wraps, as chosen), its text
 * selectable, with line numbers while lines don't wrap, and a button to copy all of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodeViewer(
    block: HtmlBlock.Code,
    text: AnnotatedString,
    style: TextStyle,
    theme: CodeTheme?,
    onDismiss: () -> Unit,
    /** What it is, above its line count; else its language, else "Code". */
    title: String? = null,
) {
    val wrapByDefault = LocalRichTextOptions.current.wrapCode
    var wrap by rememberSaveable { mutableStateOf(wrapByDefault) }

    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val lines = remember(block.code) { block.code.count { it == '\n' } + 1 }
    val sideways = rememberScrollState()
    val background = theme?.background ?: MaterialTheme.colorScheme.surface
    val foreground = theme?.foreground ?: MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.code_close))
                        }
                    },
                    title = {
                        Column {
                            Text(title ?: block.language ?: stringResource(R.string.code_title))
                            Text(
                                pluralStringResource(R.plurals.code_lines, lines, lines),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = wrap,
                            onClick = { wrap = !wrap },
                            label = { Text(stringResource(R.string.code_wrap)) },
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                            Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.code_copy))
                        }
                    },
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(background)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                ) {
                    // Numbers line up with the code only while each line is one line on screen.
                    if (!wrap) {
                        Text(
                            remember(lines) { (1..lines).joinToString("\n") },
                            style = style.copy(textAlign = TextAlign.End),
                            color = theme?.lineNumbers ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                        )
                    }
                    SelectionContainer(
                        Modifier
                            .weight(1f)
                            .then(if (wrap) Modifier.padding(start = 12.dp) else Modifier.horizontalScroll(sideways)),
                    ) {
                        Text(
                            text,
                            style = style,
                            color = foreground,
                            softWrap = wrap,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
