package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalAnimatedVisibilityScope
import pt.aguiarvieira.xmuks.core.network.ConnectionState

/**
 * Another account's room list as its own database has it: what swiping to it shows coming in,
 * before it's switched to.
 */
@Immutable
internal data class AccountPreview(
    val chats: List<RoomSummary>,
    val dms: List<RoomSummary>,
    val spaces: List<SpaceSummary>,
    val invites: List<InviteRow>,
    val profile: OwnProfile?,
    val badges: TabBadges,
    val display: RoomListDisplay,
    val login: String,
)

private data class Lists(
    val chats: List<RoomSummary>,
    val dms: List<RoomSummary>,
    val spaces: List<SpaceSummary>,
)

/** [graph]'s room list, as [HomeViewModel] shows it unfiltered. */
internal fun AccountGraph.preview(): Flow<AccountPreview> {
    val rooms = roomListRepository
    val prefs = preferences.layers()
    val lists =
        combine(rooms.chats().ordered(prefs), rooms.directMessages().ordered(prefs), rooms.topLevelSpaces(), ::Lists)
    val invited = invites.invites().map { list -> list.map { InviteRow(it, invites.media.avatar(it.avatarMxc)) } }
    val login = store.credentials()?.let { "${it.username} · ${it.serverUrl.host}" }.orEmpty()
    return combine(lists, invited, rooms.ownProfile(), rooms.tabBadges(), prefs.map(RoomListDisplay::of)) {
        lists,
        invites,
        profile,
        badges,
        display,
        ->
        AccountPreview(
            chats = lists.chats,
            dms = lists.dms,
            spaces = lists.spaces,
            invites = invites,
            profile = profile,
            badges = badges.withInvites(invites.map { it.invite }),
            display = display,
            login = login,
        )
    }
}

/**
 * [preview]'s screen on [tab], as it will look once switched to. Only to be seen: it takes no taps
 * (the finger is busy swiping), and its avatars are no one's shared elements.
 */
@Composable
internal fun AccountPreviewScreen(
    preview: AccountPreview,
    tab: HomeTab,
    now: Long,
) {
    val search = remember { SearchQueries() }
    CompositionLocalProvider(
        LocalRoomListDisplay provides preview.display,
        LocalAnimatedVisibilityScope provides null,
    ) {
        HomeScreen(
            state =
                HomeUiState(
                    tab = tab,
                    chats = preview.chats,
                    dms = preview.dms,
                    spaces = preview.spaces,
                    // Switching to it connects it: no band for how it is in the background.
                    connection = ConnectionState.Live,
                    refreshing = false,
                    account = preview.login,
                    profile = preview.profile,
                    badges = preview.badges,
                    invites = preview.invites,
                ),
            onTabChange = {},
            onRefresh = {},
            onOpenRoom = { _, _ -> },
            onOpenSpace = {},
            onAccountClick = {},
            now = now,
            search = search,
        )
    }
}
