package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** What a long press anywhere in a message does: its menu. Provided per message row. */
val LocalMessageHold = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A tap target inside a message (a picture, a file, a map): tapping does [onClick], holding still
 * opens the message's menu, as it does on the rest of the bubble.
 */
@Composable
internal fun Modifier.tapOrHold(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = combinedClickable(enabled = enabled, onLongClick = LocalMessageHold.current, onClick = onClick)
