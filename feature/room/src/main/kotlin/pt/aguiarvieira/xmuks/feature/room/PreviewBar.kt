package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** In a preview, where the composer would be: that it's only a preview, and opening the room for real. */
@Composable
internal fun PreviewBar(
    onOpenFully: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row =
        @Composable {
            Row(
                Modifier.heightIn(min = ScreenCards.BarHeight).padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.preview_banner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onOpenFully) { Text(stringResource(R.string.preview_open_room)) }
            }
        }
    val insets = modifier.windowInsetsPadding(WindowInsets.navigationBars)
    if (ScreenCards.cards) {
        // The composer's card.
        ScreenCard(insets.padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap), row)
    } else {
        // The composer's bar: on the ground along the bottom, the tab bar's height.
        Surface(insets, color = ScreenCards.ground, content = row)
    }
}
