package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Provided once around the navigation host. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Provided per destination (the navigation's animated content scope). */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Keys shared between screens so the same element morphs across a navigation.
 *
 * [scope] names the list an element was tapped in (`chats`, `space:!id`, …) and travels with the
 * navigation to the destination's header. Without it, a room shown in two lists at once — say
 * Chats (kept composed off-screen) and a space — matched itself and flew across the screen.
 */
object SharedKeys {
    fun avatar(
        id: String,
        scope: String,
    ) = "avatar:$scope:$id"

    fun title(
        id: String,
        scope: String,
    ) = "title:$scope:$id"
}

/**
 * Marks this element as the same one on the next screen: it flies and morphs (shape included)
 * between their bounds during navigation and predictive back. Outside a navigation transition
 * (previews, screenshots) it does nothing.
 */
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
        )
    }
}
