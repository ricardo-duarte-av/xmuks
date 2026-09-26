package pt.aguiarvieira.xmuks.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalAnimatedVisibilityScope
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalSharedTransitionScope
import pt.aguiarvieira.xmuks.feature.roomlist.HomeRoute
import pt.aguiarvieira.xmuks.feature.roomlist.RoomRoute
import pt.aguiarvieira.xmuks.feature.roomlist.SpaceRoute

@Serializable data object HomeKey : NavKey

@Serializable data class SpaceKey(
    val spaceId: String,
) : NavKey

/** [scope] is the list the room was opened from, so only that row's avatar flies into the header. */
@Serializable data class RoomKey(
    val roomId: String,
    val scope: String,
) : NavKey

/**
 * Home (tabs) → space → room. Phones: one pane at a time, avatars and titles flying between screens
 * (and back again under predictive back). Large screens: the list-detail strategy shows the list
 * (home or a space) beside the open room.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun XmuksNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
                sharedTransitionScope = this,
                entryProvider =
                    entryProvider {
                        entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane()) {
                            Destination {
                                HomeRoute(
                                    onOpenRoom = backStack::openRoom,
                                    onOpenSpace = { backStack.add(SpaceKey(it)) },
                                )
                            }
                        }
                        entry<SpaceKey>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                            Destination {
                                SpaceRoute(
                                    spaceId = key.spaceId,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenRoom = backStack::openRoom,
                                )
                            }
                        }
                        entry<RoomKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                            Destination {
                                RoomRoute(
                                    roomId = key.roomId,
                                    sharedScope = key.scope,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                        }
                    },
            )
        }
    }
}

/** Gives the destination's shared elements the navigation's animated scope. */
@Composable
private fun Destination(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        content = content
    )
}

/** Opening a room replaces an open one (on large screens the detail pane swaps, not stacks). */
private fun NavBackStack<NavKey>.openRoom(
    roomId: String,
    scope: String,
) {
    if (lastOrNull() is RoomKey) removeAt(lastIndex)
    add(RoomKey(roomId, scope))
}
