package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import pt.aguiarvieira.xmuks.core.data.rooms.TabBadges
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel

/** The tabs, along the bottom. */
@Composable
internal fun TabBar(
    state: HomeUiState,
    onTabChange: (HomeTab) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ShortNavigationBarDefaults.windowInsets,
) = ShortNavigationBar(modifier = modifier, containerColor = containerColor, windowInsets = windowInsets) {
    HomeTab.entries.forEach { tab ->
        ShortNavigationBarItem(
            selected = tab == state.tab,
            onClick = { onTabChange(tab) },
            icon = { TabIcon(tab, state.badges) },
            label = { Text(stringResource(tab.label)) },
        )
    }
}

/** Tab icon with the tab's unread badge: a number (red with mentions) or a quiet dot. */
@Composable
private fun TabIcon(
    tab: HomeTab,
    badges: TabBadges,
) {
    val unread =
        when (tab) {
            HomeTab.Chats -> badges.chats
            HomeTab.Dms -> badges.dms
            HomeTab.Spaces -> badges.spaces
        }
    val display = LocalRoomListDisplay.current
    val (level, count) = unread.level(display.unreadCount)
    BadgedBox(
        badge = {
            when (level) {
                UnreadLevel.None -> {}

                UnreadLevel.Dot -> {
                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                }

                UnreadLevel.Count -> {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(badgeCount(count)) }
                }

                UnreadLevel.Mention -> {
                    if (display.mentionAt) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("@" + badgeCount(count)) }
                    } else {
                        Badge { Text(badgeCount(count)) }
                    }
                }
            }
        },
    ) {
        Icon(painterResource(tab.icon), contentDescription = null)
    }
}

private fun badgeCount(count: Int) = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString()

private const val MAX_BADGE = 99

private val HomeTab.label
    get() =
        when (this) {
            HomeTab.Chats -> R.string.tab_chats
            HomeTab.Dms -> R.string.tab_dms
            HomeTab.Spaces -> R.string.tab_spaces
        }

private val HomeTab.icon
    get() =
        when (this) {
            HomeTab.Chats -> R.drawable.ic_chats
            HomeTab.Dms -> R.drawable.ic_dms
            HomeTab.Spaces -> R.drawable.ic_spaces
        }
