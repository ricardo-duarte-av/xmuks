package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadLevel

/**
 * The tabs as a screen's bottom bar, on home and in a space (under Spaces): in their own card, or
 * edge to edge on the ground.
 */
@Composable
internal fun TabBottomBar(
    selected: HomeTab,
    badges: TabBadges,
    onTabChange: (HomeTab) -> Unit,
) = if (ScreenCards.cards) {
    ScreenCard(Modifier.navigationBarsPadding().padding(ScreenCards.Gap)) {
        TabBar(selected, badges, onTabChange, containerColor = Color.Transparent, windowInsets = WindowInsets(0))
    }
} else {
    // Held to the bar height, so the room's composer bar matches it exactly.
    TabBar(
        selected,
        badges,
        onTabChange,
        ScreenCards.ground,
        Modifier.height(ScreenCards.BarHeight + navigationBarHeight())
    )
}

/** The tabs, along the bottom. */
@Composable
private fun TabBar(
    selected: HomeTab,
    badges: TabBadges,
    onTabChange: (HomeTab) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ShortNavigationBarDefaults.windowInsets,
) = ShortNavigationBar(modifier = modifier, containerColor = containerColor, windowInsets = windowInsets) {
    HomeTab.entries.forEach { tab ->
        ShortNavigationBarItem(
            selected = tab == selected,
            onClick = { onTabChange(tab) },
            icon = { TabIcon(tab, badges) },
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
