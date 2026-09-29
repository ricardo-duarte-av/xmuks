package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import javax.inject.Inject

/** Someone we ignore, with their name and avatar once their profile has loaded. */
@Immutable
data class IgnoredUser(
    val userId: String,
    val name: String?,
    val avatarMxc: String?,
)

@HiltViewModel
class IgnoredUsersViewModel
    @Inject
    constructor(
        private val contacts: Contacts,
        private val profiles: ProfileRepository,
        val media: MediaUrls,
    ) : ViewModel() {
        /** Profiles fetched so far, by user ID. */
        private val known = MutableStateFlow<Map<String, IgnoredUser>>(emptyMap())

        val users: StateFlow<List<IgnoredUser>?> =
            combine(contacts.ignored, known) { ids, profiles ->
                ids.sorted().map { profiles[it] ?: IgnoredUser(it, null, null) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val tasks = ProfileTasks(viewModelScope)

        fun unignore(userId: String) = tasks.run({ contacts.setIgnored(userId, ignore = false) })

        private val requested = mutableSetOf<String>()

        /** Few at a time: an ignore list can run to hundreds of spam accounts. */
        private val fetching = Semaphore(PARALLEL_FETCHES)

        /** A row came on screen: its profile is fetched, once. */
        fun needProfile(userId: String) {
            if (!requested.add(userId)) return
            viewModelScope.launch {
                fetching.withPermit { profiles.load(userId) }.onSuccess { profile ->
                    known.value += userId to IgnoredUser(userId, profile.displayName, profile.avatarMxc)
                }
            }
        }

        private companion object {
            const val PARALLEL_FETCHES = 4
        }
    }

@Composable
fun IgnoredUsersRoute(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IgnoredUsersViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    IgnoredUsersScreen(users, media, busy, viewModel::unignore, onOpenUser, onBack, modifier, viewModel::needProfile)
}

/** Everyone we ignore (`m.ignored_user_list`): their messages and invites don't reach us. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredUsersScreen(
    users: List<IgnoredUser>?,
    media: ProfileMedia,
    busy: Boolean,
    onUnignore: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowRow: (userId: String) -> Unit = {},
) {
    val showRow by rememberUpdatedState(onShowRow)
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = { Text(stringResource(R.string.ignored_users)) },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(padding)
                .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            if (users?.isEmpty() == true) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.ignored_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(users.orEmpty(), key = { it.userId }) { user ->
                        LaunchedEffect(user.userId) { showRow(user.userId) }
                        IgnoredRow(user, media, onUnignore, onOpenUser)
                    }
                }
            }
        }
    }
}

@Composable
private fun IgnoredRow(
    user: IgnoredUser,
    media: ProfileMedia,
    onUnignore: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val name = user.name ?: user.userId
    ListItem(
        leadingContent = { RoomAvatar(name, user.userId, media.thumbnail(user.avatarMxc), size = 40.dp) },
        supportingContent =
            if (user.name != null) {
                { Text(user.userId, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
        trailingContent = {
            TextButton(onClick = { onUnignore(user.userId) }) { Text(stringResource(R.string.unignore)) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpenUser(user.userId) },
    ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
