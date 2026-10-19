package pt.aguiarvieira.xmuks.feature.room

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import pt.aguiarvieira.xmuks.core.data.timeline.Limit

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
    /** Why the bridged network won't take one, if it won't: dimmed, and tapping it says why. */
    limitOf: (Attachment) -> Limit? = { null },
    onLimit: (Limit) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val options =
            available.map { attachment ->
                val limit = limitOf(attachment)
                Option(attachment.icon, stringResource(attachment.label), limit) {
                    onDismiss()
                    if (limit != null) onLimit(limit) else onPick(attachment)
                }
            } +
                listOfNotNull(
                    onSendAs?.let { sendAs ->
                        Option(R.drawable.ic_person, stringResource(R.string.send_as_title)) {
                            onDismiss()
                            sendAs()
                        }
                    },
                )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            // Equal columns across the whole width: as many as fit, never fewer than four (a phone's
            // two even rows of eight), nor more than there are options.
            val columns = (maxWidth / MIN_CELL).toInt().coerceIn(MIN_COLUMNS, maxOf(MIN_COLUMNS, options.size))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                options.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { option ->
                            AttachOption(
                                option.icon,
                                option.label,
                                option.onClick,
                                Modifier.weight(1f).limited(option.limit),
                            )
                        }
                        // A short last row keeps the columns above it.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private class Option(
    @param:DrawableRes val icon: Int,
    val label: String,
    val limit: Limit? = null,
    val onClick: () -> Unit,
)

@Composable
private fun AttachOption(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
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

private const val MIN_COLUMNS = 4
private val MIN_CELL = 88.dp
