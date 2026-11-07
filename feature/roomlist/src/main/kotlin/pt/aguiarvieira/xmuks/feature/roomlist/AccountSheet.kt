package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadBadge
import javax.inject.Inject

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
    ) : ViewModel() {
        val state: StateFlow<AccountsState> =
            combine(accounts.summaries(), accounts.active) { all, active ->
                AccountsState(all.firstOrNull { it.id == active }, all.filter { it.id != active })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsState())

        /** Shows [id]: the app's screens become its own. */
        fun switchTo(id: String) = accounts.switchTo(id)

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
 * away) and adding another, the other signed-in accounts with what's unread in them (tap to
 * switch), and last xmuks' own settings.
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
    onOpenSettings: () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
    onOpenSettings: () -> Unit = {},
) {
    Column(modifier.padding(bottom = 24.dp)) {
        state.current?.let { current ->
            CurrentAccount(current, onOpenProfile)
            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_add_home), null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(role = Role.Button) { onPin(current) },
            ) { Text(stringResource(R.string.account_add_shortcut)) }
        }
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_person_add), null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(role = Role.Button, onClick = onAddAccount),
        ) { Text(stringResource(R.string.account_add)) }
        if (state.others.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            state.others.forEach { OtherAccount(it, onClick = { onSwitch(it.id) }) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_settings), null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenSettings),
        ) { Text(stringResource(R.string.app_settings)) }
    }
}

/** The account shown: who it is, big, and the way to its profile. */
@Composable
private fun CurrentAccount(
    account: AccountSummary,
    onOpenProfile: (userId: String) -> Unit,
) {
    val name = account.profile?.displayName ?: account.login
    val userId = account.profile?.userId
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        RoomAvatar(name = name, id = userId ?: account.login, avatarUrl = account.profile?.avatarUrl, size = 72.dp)
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
            account.login,
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
) {
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
                listOfNotNull(account.profile?.userId, account.login).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { UnreadBadge(level, count, mentionAt = display.mentionAt) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier.clickable(
                onClickLabel = stringResource(R.string.account_switch),
                role = Role.Button,
                onClick = onClick,
            ),
    ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
