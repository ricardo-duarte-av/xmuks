package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One of a screen's cards. Screens are laid out as separate rounded cards — header, content, bars —
 * on a tinted ground ([ScreenCards.ground]), [ScreenCards.Gap] apart and from the edges.
 */
@Composable
fun ScreenCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(ScreenCards.Radius),
    color = MaterialTheme.colorScheme.surface,
    content = content,
)

object ScreenCards {
    val Gap = 8.dp
    val Radius = 28.dp

    /** The ground the cards sit on (a Scaffold's containerColor). */
    val ground @Composable get() = MaterialTheme.colorScheme.surfaceContainer
}
