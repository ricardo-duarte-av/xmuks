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
import pt.aguiarvieira.xmuks.feature.profile.IgnoredUsersRoute
import pt.aguiarvieira.xmuks.feature.profile.RoomInfoRoute
import pt.aguiarvieira.xmuks.feature.profile.RoomMembersRoute
import pt.aguiarvieira.xmuks.feature.profile.RoomPreviewRoute
import pt.aguiarvieira.xmuks.feature.profile.UserInfoRoute
import pt.aguiarvieira.xmuks.feature.room.RoomRoute
import pt.aguiarvieira.xmuks.feature.roomlist.HomeRoute
import pt.aguiarvieira.xmuks.feature.roomlist.SpaceRoute
import pt.aguiarvieira.xmuks.feature.settings.PreferencesRoute
import pt.aguiarvieira.xmuks.feature.share.ShareRequest
import pt.aguiarvieira.xmuks.feature.share.ShareRoute

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

/** One of a room's threads, from its root. */
@Serializable data class ThreadKey(
    val roomId: String,
    val rootId: String,
) : NavKey

/** Anyone's profile; our own is where it's edited, and where the account lives. */
@Serializable data class UserKey(
    val userId: String,
) : NavKey

/** gomuks' preferences: the global ones ([roomId] null), or one room's. */
@Serializable data class PreferencesKey(
    val roomId: String? = null,
) : NavKey

/** Something shared from another app, on its way to a room. */
@Serializable data class ShareKey(
    val uris: List<String>,
    val text: String?,
    val roomId: String?,
) : NavKey

/** Everyone we ignore. */
@Serializable data object IgnoredUsersKey : NavKey

/** A room's details, members and settings. */
@Serializable data class RoomInfoKey(
    val roomId: String,
) : NavKey

/** A room's member list, on its own. */
@Serializable data class RoomMembersKey(
    val roomId: String,
) : NavKey

/** A room we're not in: its preview, and joining or knocking ([eventId]: shown once in). */
@Serializable data class RoomPreviewKey(
    val roomIdOrAlias: String,
    val via: List<String> = emptyList(),
    val eventId: String? = null,
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
    share: ShareRequest? = null,
    onShareConsume: () -> Unit = {},
    links: LinkViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(HomeKey)
    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val context = LocalContext.current
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
                    backStack.add(RoomPreviewKey(target.roomIdOrAlias, target.via, target.eventId))
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
    val shareConsumed by rememberUpdatedState(onShareConsume)
    LaunchedEffect(share) {
        if (share != null) {
            backStack.add(ShareKey(share.uris, share.text, share.roomId))
            shareConsumed()
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
                                    onSendFiles = { uris -> backStack.add(ShareKey(uris, null, key.roomId)) },
                                    onOpenThread = { root -> backStack.add(ThreadKey(key.roomId, root)) },
                                )
                            }
                        }
                        entry<ThreadKey> { key ->
                            Destination {
                                RoomRoute(
                                    roomId = key.roomId,
                                    sharedScope = THREAD_SCOPE,
                                    threadRoot = key.rootId,
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
                                    onOpenMembers = { backStack.add(RoomMembersKey(key.roomId)) },
                                    onOpenPreferences = { backStack.add(PreferencesKey(key.roomId)) },
                                    onLeft = { backStack.leftRoom(key.roomId) },
                                )
                            }
                        }
                        entry<RoomPreviewKey> { key ->
                            Destination {
                                RoomPreviewRoute(
                                    roomIdOrAlias = key.roomIdOrAlias,
                                    via = key.via,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenRoom = { roomId ->
                                        backStack.remove(key)
                                        backStack.openRoom(roomId, LINK_SCOPE, key.eventId)
                                    },
                                )
                            }
                        }
                        entry<ShareKey> { key ->
                            Destination {
                                ShareRoute(
                                    request = ShareRequest(key.uris, key.text, key.roomId),
                                    onDone = { roomId ->
                                        backStack.remove(key)
                                        // Back in the room it came from, or to the one chosen.
                                        val here = (backStack.lastOrNull() as? RoomKey)?.roomId
                                        if (roomId != null && roomId != here) backStack.openRoom(roomId, LINK_SCOPE)
                                    },
                                )
                            }
                        }
                        entry<PreferencesKey> { key ->
                            Destination {
                                PreferencesRoute(roomId = key.roomId, onBack = { backStack.removeLastOrNull() })
                            }
                        }
                        entry<IgnoredUsersKey> {
                            Destination {
                                IgnoredUsersRoute(
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenUser = { backStack.add(UserKey(it)) },
                                )
                            }
                        }
                        entry<RoomMembersKey> { key ->
                            Destination {
                                RoomMembersRoute(
                                    roomId = key.roomId,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenUser = { backStack.add(UserKey(it)) },
                                )
                            }
                        }
                        entry<UserKey> { key ->
                            Destination {
                                UserInfoRoute(
                                    userId = key.userId,
                                    onOpenLink = openLink,
                                    onOpenRoom = { backStack.openRoom(it, LINK_SCOPE) },
                                    onOpenIgnoredUsers = { backStack.add(IgnoredUsersKey) },
                                    onOpenPreferences = { backStack.add(PreferencesKey()) },
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
    removeAll {
        (it is RoomKey && it.roomId == roomId) ||
            (it is RoomInfoKey && it.roomId == roomId) ||
            (it is RoomMembersKey && it.roomId == roomId)
    }
    if (isEmpty()) add(HomeKey)
}

/** A thread's header has no list row to fly from either. */
private const val THREAD_SCOPE = "thread"

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
