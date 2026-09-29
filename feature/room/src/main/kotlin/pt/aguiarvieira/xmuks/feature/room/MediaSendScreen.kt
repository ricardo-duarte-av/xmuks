package pt.aguiarvieira.xmuks.feature.room

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.media.ImageSize
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

/**
 * Before an attachment goes: what it is, for images the size to send it at (each with what it
 * would weigh), and a caption. Sending hands it to the uploads and closes this.
 */
@Composable
internal fun MediaSendScreen(
    draft: MediaDraft,
    onChoose: (ImageSize) -> Unit,
    onSend: (caption: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Dark whatever the app's theme: media reads best on black, and the controls must too.
        XmuksTheme(darkTheme = true) { MediaSendContent(draft, onChoose, onSend, onDismiss) }
    }
}

@Composable
private fun MediaSendContent(
    draft: MediaDraft,
    onChoose: (ImageSize) -> Unit,
    onSend: (caption: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val caption = rememberTextFieldState()
    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel), tint = Color.White)
                }
                Text(
                    draft.file.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Preview(draft) }
            if (draft.options.size > 1) SizeChoice(draft, onChoose)
            draft.error?.let {
                Text(
                    stringResource(R.string.attach_failed, it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    state = caption,
                    placeholder = { Text(stringResource(R.string.caption_hint)) },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { onSend(caption.text.toString()) },
                    enabled = !draft.sending,
                    modifier = Modifier.size(56.dp),
                ) {
                    if (draft.sending) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_send), stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(draft: MediaDraft) {
    val preview = draft.preview
    if (preview != null) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(preview, draft.file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (draft.file.kind == MediaKind.Video) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                    Icon(
                        painterResource(R.drawable.ic_play),
                        null,
                        tint = Color.White,
                        modifier = Modifier.padding(16.dp).size(36.dp)
                    )
                }
            }
        }
    } else {
        val context = LocalContext.current
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val icon = if (draft.file.kind == MediaKind.Audio) R.drawable.ic_audio else R.drawable.ic_file
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(draft.file.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (draft.file.size >=
                0
            ) {
                Text(
                    Formatter.formatShortFileSize(context, draft.file.size),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Original and the smaller sizes, each with its pixels and (once worked out) its weight. */
@Composable
private fun SizeChoice(
    draft: MediaDraft,
    onChoose: (ImageSize) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.options.forEach { option ->
                val name = stringResource(sizeName(option.size))
                val weight = option.bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…"
                FilterChip(
                    selected = draft.chosen == option.size,
                    onClick = { onChoose(option.size) },
                    label = { Text("$name · ${option.width}×${option.height} · $weight") },
                )
            }
        }
        if (draft.chosen == ImageSize.Original) {
            Text(
                stringResource(R.string.size_original_note),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

private fun sizeName(size: ImageSize) =
    when (size) {
        ImageSize.Original -> R.string.size_original
        ImageSize.Large -> R.string.size_large
        ImageSize.Medium -> R.string.size_medium
        ImageSize.Small -> R.string.size_small
    }
