package pt.aguiarvieira.xmuks.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import pt.aguiarvieira.xmuks.R
import pt.aguiarvieira.xmuks.core.data.links.LinkTarget
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalAnimatedVisibilityScope
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalSharedTransitionScope
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.feature.media.MediaViewerRoute
import pt.aguiarvieira.xmuks.feature.profile.RoomInfoRoute
import pt.aguiarvieira.xmuks.feature.profile.UserInfoRoute
import pt.aguiarvieira.xmuks.feature.room.RoomRoute
import pt.aguiarvieira.xmuks.feature.roomlist.HomeRoute
import pt.aguiarvieira.xmuks.feature.roomlist.SpaceRoute

@Serializable data object HomeKey : NavKey

@Serializable data class SpaceKey(
    val spaceId: String,
) : NavKey

/**
 * [scope] is the list the room was opened from, so only that row's avatar flies into the header;
 * [eventId], an event to show (a link to a message).
 */
@Serializable data class RoomKey(
    val roomId: String,
    val scope: String,
    val eventId: String? = null,
) : NavKey

/** Anyone's profile; our own is where it's edited, and where the account lives. */
@Serializable data class UserKey(
    val userId: String,
) : NavKey

/** A room's details, members and settings. */
@Serializable data class RoomInfoKey(
    val roomId: String,
) : NavKey

/** Full-screen media, over whatever opened it (never a list-detail pane). */
@Serializable data class MediaKey(
    val media: ViewerMedia,
) : NavKey

/**
 * Home (tabs) → space → room. Phones: one pane at a time, avatars and titles flying between screens
 * (and back again under predictive back). Large screens: the list-detail strategy shows the list
 * (home or a space) beside the open room.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun XmuksNavHost(
    modifier: Modifier = Modifier,
    link: String? = null,
    onLinkConsume: () -> Unit = {},
    links: LinkViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(HomeKey)
    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val openLink: (String) -> Unit = { uri ->
        scope.launch {
            when (val target = links.resolve(uri)) {
                is LinkTarget.User -> {
                    backStack.add(UserKey(target.userId))
                }

                is LinkTarget.Room -> {
                    backStack.openRoom(target.roomId, LINK_SCOPE, target.eventId)
                }

                is LinkTarget.NotJoined -> {
                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.link_not_joined, target.roomIdOrAlias),
                            Toast.LENGTH_LONG
                        ).show()
                }

                LinkTarget.Unknown -> {
                    Toast.makeText(context, R.string.link_unknown, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    NotificationPermission()
    val consumed by rememberUpdatedState(onLinkConsume)
    LaunchedEffect(link) {
        if (link != null) {
            openLink(link)
            consumed()
        }
    }
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
                                    onOpenProfile = { backStack.add(UserKey(it)) },
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
                                    jumpTo = key.eventId,
                                    onOpenLink = openLink,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenMedia = { backStack.add(MediaKey(it)) },
                                    onOpenUser = { backStack.add(UserKey(it)) },
                                    onOpenRoomInfo = { backStack.add(RoomInfoKey(key.roomId)) },
                                )
                            }
                        }
                        entry<RoomInfoKey> { key ->
                            Destination {
                                RoomInfoRoute(
                                    roomId = key.roomId,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenMedia = { backStack.add(MediaKey(it)) },
                                    onOpenUser = { backStack.add(UserKey(it)) },
                                    onLeft = { backStack.leftRoom(key.roomId) },
                                )
                            }
                        }
                        entry<UserKey> { key ->
                            Destination {
                                UserInfoRoute(
                                    userId = key.userId,
                                    onOpenLink = openLink,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenMedia = { backStack.add(MediaKey(it)) },
                                )
                            }
                        }
                        entry<MediaKey> { key ->
                            Destination {
                                MediaViewerRoute(media = key.media, onBack = { backStack.removeLastOrNull() })
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
    eventId: String? = null,
) {
    if (lastOrNull() is RoomKey) removeAt(lastIndex)
    add(RoomKey(roomId, scope, eventId))
}

/** After leaving a room: its screens go, back to the list it was opened from. */
private fun NavBackStack<NavKey>.leftRoom(roomId: String) {
    removeAll { (it is RoomKey && it.roomId == roomId) || (it is RoomInfoKey && it.roomId == roomId) }
    if (isEmpty()) add(HomeKey)
}

/** Rooms opened from a link have no list row to fly from. */
private const val LINK_SCOPE = "link"

/** Asked once logged in (Android 13+): without it, no notifications. Android stops asking after two no's. */
@Composable
private fun NotificationPermission() {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
