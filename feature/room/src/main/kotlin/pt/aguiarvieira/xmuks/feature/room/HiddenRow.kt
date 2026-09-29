package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * An event with nothing to show but that it happened: who sent it and its type, the way gomuks
 * web shows hidden events (`{ "type": "m.reaction" }`), small and out of the way.
 */
@Composable
internal fun HiddenRow(
    item: TimelineItem.Hidden,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, top = 1.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.senderName,
            color = senderColor(item.sender),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "{ \"type\": \"${item.type}\" }",
            color = muted,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            // The type is the point: it gets the room, the name what's left.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(rememberTime(item.timestamp), color = muted, style = MaterialTheme.typography.labelSmall)
    }
}
