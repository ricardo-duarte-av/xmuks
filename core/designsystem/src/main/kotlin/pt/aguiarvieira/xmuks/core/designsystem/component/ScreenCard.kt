package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * A screen's header: its app bar (and whatever rides under it) on the tinted ground, edge to edge,
 * from under the status bar. A screen's main content goes in a [ScreenBody] below it; a screen of
 * several parts lays them out as [ScreenCard]s on the ground instead.
 */
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Surface(modifier = modifier, color = ScreenCards.ground) {
    Column(Modifier.statusBarsPadding(), content = content)
}

/** A screen's main content under its [ScreenHeader]: the surface, edge to edge. */
@Composable
fun ScreenBody(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, content = content)

/** One of a screen's cards, [ScreenCards.Gap] apart and from the edges, on the tinted ground. */
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

    /**
     * The ground: headers, bottom bars, and what cards sit on (a Scaffold's containerColor). In dark
     * mode a step up from the usual container, which sits too close to the near-black surface.
     */
    val ground: Color
        @Composable get() =
            MaterialTheme.colorScheme.run { if (isLight) surfaceContainer else surfaceContainerHigh }

    /**
     * A text field's pill on the ground: white in light mode; in dark mode the lowest container is
     * near black, a hole in the bar, so the brightest surface instead.
     */
    val pill: Color
        @Composable get() = MaterialTheme.colorScheme.run { if (isLight) surfaceContainerLowest else surfaceBright }

    /** A [pill]-coloured field's colours, for a filled TextField with no indicator line. */
    val pillColors: TextFieldColors
        @Composable get() =
            TextFieldDefaults.colors(
                focusedContainerColor = pill,
                unfocusedContainerColor = pill,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )

    /** The height of a bar along the bottom: the tab bar's, which the room's composer matches. */
    val BarHeight = 64.dp
}

private val ColorScheme.isLight get() = surface.luminance() > 0.5f
