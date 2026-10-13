package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pt.aguiarvieira.xmuks.core.richtext.LocalMessageHold

/**
 * A tap target inside a message (a picture, a file, a map): tapping does [onClick], holding still
 * opens the message's menu, as it does on the rest of the bubble.
 */
@Composable
internal fun Modifier.tapOrHold(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = combinedClickable(enabled = enabled, onLongClick = LocalMessageHold.current, onClick = onClick)
