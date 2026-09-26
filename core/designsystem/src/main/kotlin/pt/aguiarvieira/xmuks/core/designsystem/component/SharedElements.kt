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

/** Keys shared between screens so the same element morphs across a navigation. */
object SharedKeys {
    fun avatar(id: String) = "avatar:$id"

    fun title(id: String) = "title:$id"

    fun container(id: String) = "container:$id"
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
