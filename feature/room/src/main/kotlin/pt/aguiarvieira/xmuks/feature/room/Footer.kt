package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeDelivery
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import java.text.DateFormat
import java.util.Date

/** The short local time of [timestamp]. */
@Composable
internal fun rememberTime(timestamp: Long): String =
    remember(timestamp) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp)) }

@Composable
internal fun Footer(
    message: TimelineItem.Message,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val time = rememberTime(message.timestamp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SendStatus(message.sendState)
        message.sendError?.takeIf { message.localId == null }?.let {
            Text(
                stringResource(R.string.send_failed, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (message.edited) {
            Text(
                stringResource(R.string.edited),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(time, color = color, style = MaterialTheme.typography.labelSmall)
        message.bridgeDelivery?.let { BridgeTicks(it, color) }
    }
}

/** Ours in a bridged room: ✓ the network has it, ✓✓ it reached them, or the bridge failed it. */
@Composable
private fun BridgeTicks(
    delivery: BridgeDelivery,
    color: Color,
) {
    val (icon, label) =
        when (delivery) {
            BridgeDelivery.Sent -> R.drawable.ic_check to R.string.bridge_sent
            BridgeDelivery.Delivered -> R.drawable.ic_done_all to R.string.bridge_delivered
            BridgeDelivery.Failed -> R.drawable.ic_error to R.string.bridge_failed
        }
    val tint = if (delivery == BridgeDelivery.Failed) MaterialTheme.colorScheme.error else color
    Icon(
        painterResource(icon),
        contentDescription = stringResource(label),
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}

/** Our messages that aren't out yet: a clock while sending, a warning when it needs a decision. */
@Composable
private fun SendStatus(state: SendState) {
    val (icon, label, tint) =
        when (state) {
            SendState.Sent -> {
                return
            }

            SendState.Sending -> {
                Triple(
                    R.drawable.ic_clock,
                    R.string.sending,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SendState.Failed -> {
                Triple(R.drawable.ic_error, R.string.not_sent, MaterialTheme.colorScheme.error)
            }

            SendState.Unknown -> {
                Triple(R.drawable.ic_error, R.string.maybe_not_sent, MaterialTheme.colorScheme.error)
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        if (state != SendState.Sending) {
            Text(stringResource(label), color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}
