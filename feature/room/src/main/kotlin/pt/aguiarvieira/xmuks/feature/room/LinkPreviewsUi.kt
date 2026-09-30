package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The draft's link previews, and asking for or dropping one. */
class LinkPreviewsUi(
    val items: List<ComposerPreview> = emptyList(),
    val onLoad: (String) -> Unit = {},
    val onDismiss: (String) -> Unit = {},
)

/**
 * Above the message box, one chip per link written: "Preview" fetches it (through the
 * homeserver), then it shows what will be bundled, with an ✕ to leave it out.
 */
@Composable
internal fun ComposerLinkPreviews(previews: LinkPreviewsUi) {
    if (previews.items.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        previews.items.forEach { item ->
            val ready = item.ready
            if (ready != null) {
                InputChip(
                    selected = true,
                    onClick = { previews.onDismiss(item.url) },
                    label = { ChipText(ready.preview?.title ?: item.url) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_link), null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            stringResource(R.string.link_preview_remove),
                            Modifier.size(18.dp)
                        )
                    },
                )
            } else {
                AssistChip(
                    onClick = { if (!item.loading) previews.onLoad(item.url) },
                    label = {
                        ChipText(
                            stringResource(
                                if (item.failed) R.string.link_preview_failed else R.string.link_preview_load,
                                item.url
                            )
                        )
                    },
                    leadingIcon = {
                        if (item.loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.ic_link), null, Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChipText(text: String) =
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = CHIP_TEXT))

private val CHIP_TEXT = 240.dp
