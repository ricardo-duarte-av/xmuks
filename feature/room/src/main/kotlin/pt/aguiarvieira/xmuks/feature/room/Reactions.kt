package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction

@Composable
internal fun Reactions(
    reactions: List<Reaction>,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { r ->
            Surface(
                shape = CircleShape,
                color = if (r.mine) colors.secondaryContainer else colors.surfaceContainerHigh,
                border = if (r.mine) androidx.compose.foundation.BorderStroke(1.dp, colors.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (r.isImage) {
                        AsyncImage(
                            model = resolver.media(r.key, false),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(r.key.take(MAX_REACTION_CHARS), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        r.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val MAX_REACTION_CHARS = 24
