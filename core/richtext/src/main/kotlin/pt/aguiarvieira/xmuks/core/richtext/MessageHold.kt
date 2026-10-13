package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.runtime.staticCompositionLocalOf

/** What a long press anywhere in a message does: its menu. Provided per message row. */
val LocalMessageHold = staticCompositionLocalOf<(() -> Unit)?> { null }
