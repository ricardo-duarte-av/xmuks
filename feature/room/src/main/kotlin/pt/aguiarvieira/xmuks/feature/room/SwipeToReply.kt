package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import kotlin.math.roundToInt

/**
 * Drag [content] towards the end (right, in left-to-right) past a threshold and let go: [onReply].
 * Horizontal only, and only once the finger has clearly moved sideways, so the timeline's vertical
 * scroll, taps and long presses behave as before. With [enabled] false, it doesn't move.
 */
@Composable
internal fun SwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val reply by rememberUpdatedState(onReply)
    val state =
        rememberDraggableState { delta ->
            drag = (drag + delta).coerceIn(0f, threshold * OVERSHOOT)
            val past = drag >= threshold
            if (past && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            armed = past
        }
    Box(
        modifier.draggable(
            state = state,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            reverseDirection = LocalLayoutDirection.current == LayoutDirection.Rtl,
            onDragStopped = {
                if (armed) reply()
                armed = false
                animate(drag, 0f, animationSpec = spring()) { value, _ -> drag = value }
            },
        ),
    ) {
        // The reply arrow, uncovered as the message slides away from it.
        Icon(
            painterResource(R.drawable.ic_reply),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(ICON_SIZE)
                    .graphicsLayer {
                        val progress = (drag / threshold).coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = ICON_MIN_SCALE + (1 - ICON_MIN_SCALE) * progress
                        scaleY = scaleX
                    },
        )
        Box(Modifier.offset { IntOffset(drag.roundToInt(), 0) }) { content() }
    }
}

/** A swipe's reply: the composer answers [message], unless the bridged network takes no replies (then it says so). */
internal fun ComposerActions.replyOrSayWhy(message: TimelineItem.Message) {
    val block = features?.replyBlock()
    if (block != null) onLimit(block) else onReply(message)
}

private val SWIPE_THRESHOLD = 64.dp
private const val OVERSHOOT = 1.4f
private val ICON_SIZE = 24.dp
private const val ICON_MIN_SCALE = 0.6f
