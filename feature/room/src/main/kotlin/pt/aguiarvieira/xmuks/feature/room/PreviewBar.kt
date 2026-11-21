package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Column
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
import pt.aguiarvieira.xmuks.core.data.roominfo.SendBlock
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** In a preview, where the composer would be: that it's only a preview, and opening the room for real. */
@Composable
internal fun PreviewBar(
    onOpenFully: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerStandIn(
        stringResource(R.string.preview_banner),
        modifier,
        action = stringResource(R.string.preview_open_room) to onOpenFully,
    )
}

/**
 * Where the composer would be, when we can't post: the room was replaced (and a way to the new
 * one), or our power level is too low to send messages.
 */
@Composable
internal fun SendBlockBar(
    block: SendBlock,
    onOpenRoom: (roomId: String, via: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is SendBlock.Replaced -> {
            ComposerStandIn(
                stringResource(R.string.room_replaced_bar),
                modifier,
                action = stringResource(R.string.room_replaced_open) to { onOpenRoom(block.roomId, block.via) },
            )
        }

        is SendBlock.NoPermission -> {
            ComposerStandIn(
                stringResource(R.string.send_not_allowed),
                modifier,
                detail = stringResource(R.string.send_not_allowed_levels, block.needed, block.mine),
            )
        }
    }
}

/** The composer's card (or bar) with [text] in it, and maybe a detail line and one button. */
@Composable
private fun ComposerStandIn(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    val row =
        @Composable {
            Row(
                Modifier.heightIn(min = ScreenCards.BarHeight).padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (detail != null) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                action?.let { (label, onClick) -> FilledTonalButton(onClick = onClick) { Text(label) } }
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
