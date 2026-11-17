package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * Paging between the signed-in accounts (in the order they were added) by swiping the room list's
 * header: towards the start for the next one, towards the end for the previous one. The whole
 * screen follows the finger, the other account's ([AccountPager]'s preview) coming in beside it;
 * let go past [COMMIT] of the width, or flick, and it finishes the turn and switches; otherwise it
 * springs back. Past the first or last account it only gives a little.
 *
 * [drag] is in the reading direction's terms: positive towards the end (the previous account
 * coming in from the start), negative towards the start (the next one, from the end).
 */
@Stable
class AccountSwipeState internal constructor(
    private val haptics: HapticFeedback,
    private val fling: Float,
    private val give: Float,
) {
    var previous: String? by mutableStateOf(null)
    var next: String? by mutableStateOf(null)
    var onSwitch: (id: String) -> Unit = {}

    /** Right-to-left: the start is on the right. */
    var rtl = false

    /** The screen reader's names for going to the next and the previous account. */
    var labels = "" to ""

    var drag by mutableFloatStateOf(0f)
        private set

    /** The screen's width: a full turn. */
    var width by mutableIntStateOf(0)

    private var armed = false

    /** The account whose screen is coming in; null at rest, or with no account that way. */
    val incoming: String? by derivedStateOf {
        when {
            drag > 0f -> previous
            drag < 0f -> next
            else -> null
        }
    }

    val enabled: Boolean get() = previous != null || next != null

    private fun target(towards: Float) = if (towards < 0) next else previous

    val draggable =
        DraggableState { delta ->
            if (width == 0) return@DraggableState
            val towards = if (drag != 0f) drag else delta
            if (target(towards) == null) {
                // Nowhere to go that way: it gives, but not much.
                drag = (drag + delta * RESISTANCE).coerceIn(-give, give)
                armed = false
            } else {
                drag = (drag + delta).coerceIn(-width.toFloat(), width.toFloat())
                val past = target(drag) != null && abs(drag) >= width * COMMIT
                if (past && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                armed = past
            }
        }

    /** Let go at [velocity]: the turn finishes and the account switches, or the screen springs back. */
    suspend fun release(velocity: Float) {
        val to = target(drag)?.takeIf { armed || flung(velocity) }
        armed = false
        if (to == null) {
            animate(drag, 0f, initialVelocity = velocity, animationSpec = spring()) { value, _ -> drag = value }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        val end = sign(drag) * width
        animate(drag, end, initialVelocity = velocity, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
            value,
            _,
            ->
            drag = value
        }
        // The account's own screen takes over from its preview, and this one goes.
        onSwitch(to)
        // Still here (it couldn't switch, say it was just logged out): back to this account.
        delay(SETTLE_MS)
        animate(drag, 0f, animationSpec = spring()) { value, _ -> drag = value }
    }

    /** Let go at [velocity] (at least a flick) the way the screen was dragged. */
    private fun flung(velocity: Float) = abs(velocity) >= fling && sign(velocity) == sign(drag)
}

@Composable
internal fun rememberAccountSwipe(
    previous: String?,
    next: String?,
    onSwitch: (id: String) -> Unit,
): AccountSwipeState {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val state =
        remember(haptics, density) {
            with(density) { AccountSwipeState(haptics, FLING_VELOCITY.toPx(), EDGE_GIVE.toPx()) }
        }
    state.previous = previous
    state.next = next
    state.onSwitch = onSwitch
    state.rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    state.labels = stringResource(R.string.account_next) to stringResource(R.string.account_previous)
    return state
}

/**
 * Where the swipe starts (the header): the drag, and for screen readers an action for each account
 * there is to go to.
 */
internal fun Modifier.accountSwipe(state: AccountSwipeState?): Modifier {
    if (state == null) return this
    val (nextLabel, previousLabel) = state.labels
    val actions =
        listOfNotNull(state.next?.let { nextLabel to it }, state.previous?.let { previousLabel to it })
            .map { (label, id) ->
                CustomAccessibilityAction(label) {
                    state.onSwitch(id)
                    true
                }
            }
    return semantics { customActions = actions }
        .draggable(
            state = state.draggable,
            orientation = Orientation.Horizontal,
            enabled = state.enabled,
            reverseDirection = state.rtl,
            onDragStopped = { velocity -> state.release(velocity) },
        )
}

/**
 * [content] (the account's screen), moved by [state]'s swipe, with the [preview] of the account
 * coming in beside it while it's swiped.
 */
@Composable
internal fun AccountPager(
    state: AccountSwipeState,
    preview: @Composable (id: String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().clipToBounds().onSizeChanged { state.width = it.width }) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = state.drag.onScreen(state.rtl) }) {
            content()
        }
        state.incoming?.let { id ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val from = if (state.drag > 0f) -state.width else state.width
                    translationX = (state.drag + from).onScreen(state.rtl)
                },
            ) { preview(id) }
        }
    }
}

/** A distance towards the end, as a translation on screen (the end is the left in right-to-left). */
private fun Float.onScreen(rtl: Boolean) = if (rtl) -this else this

/** The share of the width past which letting go switches. */
private const val COMMIT = 0.3f

/** A flick this fast (dp per second) switches however short it was. */
private val FLING_VELOCITY = 600.dp

/** How far the screen gives when there's no account that way. */
private val EDGE_GIVE = 40.dp

/** How much of the finger's travel the screen follows when there's no account that way. */
private const val RESISTANCE = 0.25f

/** Longer than the hand-over from preview to the account's screen. */
private const val SETTLE_MS = 1_000L
