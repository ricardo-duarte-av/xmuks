package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        message.sendError?.let {
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
    }
}
