package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun UnreadBadge(
    level: UnreadLevel,
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (level == UnreadLevel.None) return
    val colors = MaterialTheme.colorScheme
    when (level) {
        UnreadLevel.None -> {
            error("handled above")
        }

        UnreadLevel.Dot -> {
            Box(
                modifier
                    .size(10.dp)
                    .background(colors.primary, CircleShape)
                    .semantics { contentDescription = "Unread" },
            )
        }

        UnreadLevel.Count, UnreadLevel.Mention -> {
            val mention = level == UnreadLevel.Mention
            Box(
                modifier =
                    modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .background(if (mention) colors.error else colors.primary, CircleShape)
                        .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > MAX_SHOWN) "$MAX_SHOWN+" else count.toString(),
                    color = if (mention) colors.onError else colors.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private const val MAX_SHOWN = 99
