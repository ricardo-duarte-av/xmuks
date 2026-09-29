package pt.aguiarvieira.xmuks.feature.room

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The + button's sheet: what can be attached ([available], in order), and "Send as" for choosing
 * a per-message profile when there are any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSheet(
    available: List<Attachment>,
    onPick: (Attachment) -> Unit,
    onSendAs: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = PER_ROW,
        ) {
            available.forEach { attachment ->
                AttachOption(attachment.icon, stringResource(attachment.label)) {
                    onDismiss()
                    onPick(attachment)
                }
            }
            if (onSendAs != null) {
                AttachOption(R.drawable.ic_person, stringResource(R.string.send_as_title)) {
                    onDismiss()
                    onSendAs()
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(
                    OPTION_WIDTH
                ).clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private const val PER_ROW = 4
private val OPTION_WIDTH = 80.dp
