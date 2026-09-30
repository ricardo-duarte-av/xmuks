package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Reader
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** A reader's avatar on a message, and whether it's coming, staying, or going. */
internal class ShownReader(
    var reader: Reader,
    val visible: MutableTransitionState<Boolean>,
)

/**
 * The readers shown on one message, animated: those arriving grow and fade in, those leaving
 * (their receipt moved on) shrink and fade out before they go. The first readers shown when the
 * message comes into view just appear.
 */
internal class ReceiptStack(
    initial: List<Reader>,
) {
    val shown =
        mutableStateListOf<ShownReader>().apply {
            initial.forEach { add(ShownReader(it, MutableTransitionState(true))) }
        }

    fun update(readers: List<Reader>) {
        val ids = readers.mapTo(HashSet()) { it.userId }
        shown.forEach { if (it.reader.userId !in ids) it.visible.targetState = false }
        readers.forEachIndexed { i, reader ->
            val existing = shown.firstOrNull { it.reader.userId == reader.userId }
            if (existing != null) {
                existing.reader = reader
                existing.visible.targetState = true
            } else {
                shown.add(
                    i.coerceAtMost(shown.size),
                    ShownReader(
                        reader,
                        MutableTransitionState(false).apply {
                            targetState =
                                true
                        }
                    )
                )
            }
        }
        // Gone for good once their exit has played.
        shown.removeAll { !it.visible.targetState && !it.visible.currentState && it.visible.isIdle }
    }
}

@Composable
internal fun rememberReceiptStack(readers: List<Reader>): ReceiptStack {
    val stack = remember { ReceiptStack(readers) }
    SideEffect { stack.update(readers) }
    // An exit finishing changes the transition states: read them so that recomposes and clears it.
    stack.shown.forEach { it.visible.isIdle }
    return stack
}

/** Up to [max] overlapping avatars, the newest reader on top at the right. */
@Composable
internal fun ReceiptAvatars(
    stack: ReceiptStack,
    max: Int,
    avatarUrl: (String?) -> String?,
    ring: Dp,
    step: Dp,
    avatar: Dp,
) {
    val shown = stack.shown.take(max)
    Box(Modifier.width(ring + step * (shown.size - 1).coerceAtLeast(0))) {
        // Drawn back to front, so the newest reader sits on top at the right.
        shown.asReversed().forEachIndexed { index, entry ->
            AnimatedVisibility(
                visibleState = entry.visible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.offset(x = step * index),
            ) {
                Box(Modifier.size(ring).background(MaterialTheme.colorScheme.surface, CircleShape).padding(1.dp)) {
                    RoomAvatar(entry.reader.name, entry.reader.userId, avatarUrl(entry.reader.avatarMxc), size = avatar)
                }
            }
        }
    }
}
