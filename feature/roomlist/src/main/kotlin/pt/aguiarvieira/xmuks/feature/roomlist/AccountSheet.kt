package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.account.AccountSummary
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadBadge
import javax.inject.Inject

/** The sheet's drag handle (Material's: 22 dp above and below a 4 dp bar). */
private val DRAG_HANDLE = 48.dp

/** The divider under the shown account, with the space around it. */
private val DIVIDER_PADDING = 8.dp
private val DIVIDER_SPACE = DIVIDER_PADDING * 2 + 1.dp

/** Below this the colour scheme is a dark one. */
private const val HALF = 0.5f

/** The least the shown account takes, on a window too short for half of it to be enough. */
private val MIN_CURRENT_HEIGHT = 280.dp

/** The signed-in accounts: the one shown, and the others to switch to. */
@Immutable
data class AccountsState(
    val current: AccountSummary? = null,
    val others: List<AccountSummary> = emptyList(),
) {
    /** Another account has something unread: the account chip says so. */
    val othersUnread: Boolean get() = others.any { it.unread.any }
}

@HiltViewModel
class AccountsViewModel
    @Inject
    constructor(
        private val accounts: Accounts,
        private val session: SessionRepository,
    ) : ViewModel() {
        val state: StateFlow<AccountsState> =
            combine(accounts.summaries(), accounts.active) { all, active ->
                AccountsState(all.firstOrNull { it.id == active }, all.filter { it.id != active })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsState())

        /** Shows [id]: the app's screens become its own. */
        fun switchTo(id: String) = accounts.switchTo(id)

        /** Signs [account] out, shown or not: it leaves the switcher, and this phone forgets it. */
        fun logout(account: AccountSummary) {
            viewModelScope.launch { session.logout(account.id) }
        }

        /** Puts [account] on the home screen: tapping it shows the account, on its room list. */
        fun pinAccount(
            account: AccountSummary,
            onRefused: () -> Unit,
        ) {
            viewModelScope.launch {
                if (!accounts.graph(account.id).shortcuts.pinAccount(account.profile, account.login)) onRefused()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/**
 * The account switcher, from our avatar on the room list: the account shown (its profile a tap
 * away), adding another and xmuks' own settings in its corners, then the other signed-in accounts
 * with what's unread in them (tap to switch). A long press on any account offers logging it out
 * and putting it on the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(
    state: AccountsState,
    onOpenProfile: (userId: String) -> Unit,
    onSwitch: (id: String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
    onPin: (AccountSummary) -> Unit = {},
    onLogout: (AccountSummary) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // In the dark the scrim hardly darkens the room list, so the sheet stands out by being lighter.
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < HALF
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) colors.surfaceContainerHigh else BottomSheetDefaults.ContainerColor,
    ) {
        AccountSheetContent(
            state = state,
            onOpenProfile = { userId ->
                onOpenProfile(userId)
                onDismiss()
            },
            onSwitch = { id ->
                onSwitch(id)
                onDismiss()
            },
            onAddAccount = {
                onAddAccount()
                onDismiss()
            },
            onPin = {
                onPin(it)
                onDismiss()
            },
            onLogout = {
                onLogout(it)
                onDismiss()
            },
            onOpenSettings = {
                onOpenSettings()
                onDismiss()
            },
        )
    }
}

@Composable
internal fun AccountSheetContent(
    state: AccountsState,
    onOpenProfile: (userId: String) -> Unit,
    onSwitch: (id: String) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onPin: (AccountSummary) -> Unit = {},
    onLogout: (AccountSummary) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var loggingOut by remember { mutableStateOf<AccountSummary?>(null) }
    val actions = AccountActions(onPin = onPin, onLogout = { loggingOut = it })
    // Under the navigation bar when the sheet is half open: kept clear, so it stops on the divider.
    val navigation = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Column(modifier) {
        // The sheet opens half way: the shown account fills that half, down to the divider.
        Box(Modifier.fillMaxWidth().height(halfSheetHeight(navigation))) {
            state.current?.let { CurrentAccount(it, onOpenProfile, actions) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onAddAccount) {
                    Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.account_add))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.app_settings))
                }
            }
        }
        if (state.others.isEmpty()) {
            // No divider with no one under it, but the sheet stays as tall.
            Spacer(Modifier.height(DIVIDER_SPACE + navigation))
        } else {
            HorizontalDivider(
                Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = DIVIDER_PADDING,
                    bottom =
                        DIVIDER_PADDING + navigation
                ),
            )
            state.others.forEach { OtherAccount(it, onClick = { onSwitch(it.id) }, actions) }
            Spacer(Modifier.height(24.dp))
        }
    }
    loggingOut?.let { account ->
        val name = account.profile?.displayName ?: account.login
        AlertDialog(
            onDismissRequest = { loggingOut = null },
            title = { Text(stringResource(R.string.account_logout_title, name)) },
            text = { Text(stringResource(R.string.account_logout_text)) },
            confirmButton = {
                TextButton(onClick = {
                    loggingOut = null
                    onLogout(account)
                }) { Text(stringResource(R.string.account_logout)) }
            },
            dismissButton = { TextButton(onClick = { loggingOut = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** What a long press on an account offers. */
private class AccountActions(
    val onPin: (AccountSummary) -> Unit,
    val onLogout: (AccountSummary) -> Unit,
)

/**
 * The height that, under the sheet's drag handle, reaches the bottom of the half-open sheet (half
 * the window, less the [navigation] bar) with room left for the divider.
 */
@Composable
private fun halfSheetHeight(navigation: Dp): Dp {
    val half = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height / 2).toDp() }
    return (half - navigation - DRAG_HANDLE - DIVIDER_SPACE).coerceAtLeast(MIN_CURRENT_HEIGHT)
}

/** [account]'s long-press menu: log it out, or put it on the home screen. */
@Composable
private fun AccountMenu(
    account: AccountSummary,
    expanded: Boolean,
    actions: AccountActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.account_logout)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_logout), null) },
            onClick = {
                onDismiss()
                actions.onLogout(account)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.account_add_shortcut)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_add_home), null) },
            onClick = {
                onDismiss()
                actions.onPin(account)
            },
        )
    }
}

/** The account shown: who it is, as big as the space allows, and the way to its profile. */
@Composable
private fun CurrentAccount(
    account: AccountSummary,
    onOpenProfile: (userId: String) -> Unit,
    actions: AccountActions,
) {
    val name = account.profile?.displayName ?: account.login
    val userId = account.profile?.userId
    var menu by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClickLabel = userId?.let { stringResource(R.string.account_profile) },
                    onLongClickLabel = stringResource(R.string.account_options),
                    onClick = { userId?.let(onOpenProfile) },
                    onLongClick = { menu = true },
                ).padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoomAvatar(
                name = name,
                id = userId ?: account.login,
                avatarUrl = account.profile?.avatarUrl,
                size = min(maxWidth, maxHeight),
                // The thumbnail at once, then the original over it: only that one animates.
                fullUrl = account.profile?.fullAvatarUrl,
            )
            AccountMenu(account, menu, actions) { menu = false }
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        userId?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            account.server,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (userId != null) {
            FilledTonalButton(onClick = { onOpenProfile(userId) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.account_profile))
            }
        }
    }
}

/** Another signed-in account: who, on which gomuks, and what's unread there. */
@Composable
private fun OtherAccount(
    account: AccountSummary,
    onClick: () -> Unit,
    actions: AccountActions,
) {
    var menu by remember { mutableStateOf(false) }
    val name = account.profile?.displayName ?: account.login
    val display = LocalRoomListDisplay.current
    val (level, count) = account.unread.level(display.unreadCount)
    ListItem(
        leadingContent = {
            RoomAvatar(
                name = name,
                id = account.profile?.userId ?: account.login,
                avatarUrl = account.profile?.avatarUrl,
                size = 40.dp
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(account.profile?.userId, account.server).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { UnreadBadge(level, count, mentionAt = display.mentionAt) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier.combinedClickable(
                onClickLabel = stringResource(R.string.account_switch),
                onLongClickLabel = stringResource(R.string.account_options),
                role = Role.Button,
                onClick = onClick,
                onLongClick = { menu = true },
            ),
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AccountMenu(account, menu, actions) { menu = false }
    }
}
