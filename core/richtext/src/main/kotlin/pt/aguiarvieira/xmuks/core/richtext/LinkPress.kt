package pt.aguiarvieira.xmuks.core.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult

/** What pressing a link does: a tap opens it, a hold copies it. */
internal class LinkPress(
    val open: (String) -> Unit,
    val copy: (String) -> Unit,
)

@Composable
internal fun rememberLinkPress(): LinkPress {
    val uriHandler = LocalUriHandler.current

    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    return remember(uriHandler, clipboard, haptics) {
        LinkPress(
            open = uriHandler::openUri,
            copy = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setText(AnnotatedString(it))
            },
        )
    }
}

/**
 * A press on one of the text's web links (or mention pills) is the link's alone: tapped it opens,
 * held it's copied. Neither the text's own link handling nor the message's hold (its menu) sees it;
 * a drag still scrolls. [layout] is the text's, once laid out.
 */
internal fun Modifier.linkPresses(
    layout: () -> TextLayoutResult?,
    press: LinkPress,
): Modifier =
    pointerInput(press) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val url = layout()?.urlAt(down.position) ?: return@awaitEachGesture
            down.consume()
            val released =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    awaitRelease(down, viewConfiguration.touchSlop)
                }
            // Held past the timeout: copied. Lifted in place: opened. Moved off (a scroll): neither.
            if (released == null) {
                press.copy(url)
                consumeUntilUp()
            } else if (released) {
                press.open(url)
            }
        }
    }

/** The web link (or pill) under [position], if it's on a line's text rather than beside it. */
private fun TextLayoutResult.urlAt(position: Offset): String? {
    if (position.y < 0 || position.y >= size.height) return null
    val line = getLineForVerticalPosition(position.y)
    if (position.x < getLineLeft(line) || position.x > getLineRight(line)) return null
    val offset = getOffsetForPosition(position)
    return layoutInput.text
        .getLinkAnnotations(offset, offset + 1)
        .firstNotNullOfOrNull { (it.item as? LinkAnnotation.Url)?.url }
}

/** True once lifted where it went down; false if it moved off (a scroll) or something else took it. */
private suspend fun AwaitPointerEventScope.awaitRelease(
    down: PointerInputChange,
    slop: Float,
): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (change.changedToUp()) {
            change.consume()
            return true
        }
        if (change.isConsumed || (change.position - down.position).getDistance() > slop) return false
    }
}

/** Swallows the rest of a held press, so nothing behind it acts on the lift. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
