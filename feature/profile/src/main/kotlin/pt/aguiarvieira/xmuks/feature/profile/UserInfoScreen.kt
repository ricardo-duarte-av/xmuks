package pt.aguiarvieira.xmuks.feature.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.richtext.SafeUriHandler

/** What we can change on our own profile; absent on anyone else's. */
@Immutable
class ProfileEdits(
    val setDisplayName: (String) -> Unit,
    val setBio: (String) -> Unit,
    val setStatus: (text: String, emoji: String) -> Unit,
    val setPronouns: (String) -> Unit,
    val setTimezone: (String?) -> Unit,
    val setAvatar: (Uri?) -> Unit,
    val setBanner: (Uri?) -> Unit,
    val personas: PersonaEdits,
    val logout: () -> Unit,
)

/** `mxc://` to loadable URLs: a thumbnail, and the full image for the viewer. */
@Immutable
class ProfileMedia(
    val thumbnail: (String?) -> String?,
    val full: (String?) -> String?,
) {
    fun viewer(
        mxc: String?,
        title: String?,
    ): ViewerMedia? =
        full(mxc)?.let { ViewerMedia(ViewerMedia.Kind.Image, it, previewUrl = thumbnail(mxc), title = title) }
}

@Composable
fun UserInfoRoute(
    userId: String,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (uri: String) -> Unit = {},
    onOpenRoom: (roomId: String) -> Unit = {},
    onOpenIgnoredUsers: () -> Unit = {},
    viewModel: UserInfoViewModel =
        hiltViewModel<UserInfoViewModel, UserInfoViewModel.Factory>(key = userId) { it.create(userId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isMe by viewModel.isMe.collectAsStateWithLifecycle()
    val personas by viewModel.perMessageProfiles.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val error by viewModel.tasks.error.collectAsStateWithLifecycle()
    val ignored by viewModel.ignored.collectAsStateWithLifecycle()
    val mutualRooms by viewModel.mutualRooms.collectAsStateWithLifecycle()
    val directRoom by viewModel.directRoom.collectAsStateWithLifecycle()
    val openRoom by rememberUpdatedState(onOpenRoom)
    LaunchedEffect(viewModel) { viewModel.openRoom.collect { openRoom(it) } }
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    val edits =
        remember(viewModel) {
            ProfileEdits(
                setDisplayName = viewModel::setDisplayName,
                setBio = viewModel::setBio,
                setStatus = viewModel::setStatus,
                setPronouns = viewModel::setPronouns,
                setTimezone = viewModel::setTimezone,
                setAvatar = viewModel::setAvatar,
                setBanner = viewModel::setBanner,
                personas = PersonaEdits.of(viewModel.personas),
                logout = viewModel::logout,
            )
        }
    val context = LocalContext.current
    val uriHandler = remember(context, onOpenLink) { SafeUriHandler(context, onOpenLink) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        UserInfoScreen(
            userId = userId,
            state = state,
            media = media,
            busy = busy,
            error = error,
            onErrorShow = viewModel.tasks::errorShown,
            onRetry = viewModel::refresh,
            onBack = onBack,
            onOpenMedia = onOpenMedia,
            modifier = modifier,
            own = if (isMe) OwnProfile(edits, personas, viewModel.account, connection, onOpenIgnoredUsers) else null,
            other =
                if (isMe) {
                    null
                } else {
                    OtherProfile(
                        hasDirectRoom = directRoom != null,
                        ignored = ignored,
                        mutualRooms = mutualRooms,
                        onMessage = viewModel::message,
                        onSetIgnored = viewModel::setIgnored,
                        onOpenRoom = onOpenRoom,
                    )
                },
        )
    }
}

/** Only on our own profile: the edits, our per-message profiles and the account. */
@Immutable
class OwnProfile(
    val edits: ProfileEdits,
    val personas: PerMessageProfiles,
    val account: String,
    val connection: ConnectionState,
    val onOpenIgnoredUsers: () -> Unit = {},
)

/**
 * Anyone's profile: banner, avatar, name, status, pronouns, local time and biography. On our own,
 * every one of them can be changed, and our per-message profiles and the account (log out) are here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
    userId: String,
    state: ProfileState,
    media: ProfileMedia,
    busy: Boolean,
    error: String?,
    onErrorShow: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
    own: OwnProfile? = null,
    other: OtherProfile? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = error?.let { stringResource(R.string.action_failed, it) }
    val errorShown by rememberUpdatedState(onErrorShow)
    LaunchedEffect(failed) {
        if (failed != null) {
            errorShown()
            snackbar.showSnackbar(failed)
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        snackbarHost = { SnackbarHost(snackbar) },
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
                        title = {
                            val name = (state as? ProfileState.Loaded)?.profile?.displayName ?: userId
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
    ) { padding ->
        when (state) {
            ProfileState.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ProfileState.Failed -> {
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.profile_load_failed, state.message))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }

            is ProfileState.Loaded -> {
                ProfileCards(state.profile, media, own, other, onOpenMedia, padding)
            }
        }
    }
}

@Composable
private fun ProfileCards(
    profile: UserProfile,
    media: ProfileMedia,
    own: OwnProfile?,
    other: OtherProfile?,
    onOpenMedia: (ViewerMedia) -> Unit,
    padding: PaddingValues,
) {
    val cardModifier = Modifier.fillMaxWidth()
    LazyColumn(
        modifier = Modifier.padding(padding).padding(horizontal = ScreenCards.Gap).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(ScreenCards.Gap),
        contentPadding = PaddingValues(bottom = ScreenCards.Gap),
    ) {
        item(key = "hero") { HeroCard(profile, media, own?.edits, onOpenMedia, cardModifier) }
        if (other != null) item(key = "contact") { ContactCard(other, cardModifier) }
        item(key = "details") { DetailsCard(profile, own?.edits, cardModifier) }
        item(key = "about") { AboutCard(profile, media, own?.edits, onOpenMedia, cardModifier) }
        other?.mutualRooms?.let { rooms ->
            item(key = "mutual") { MutualRoomsCard(rooms, other.onOpenRoom, cardModifier) }
        }
        if (own != null) {
            item(key = "personas") { PersonasCard(own.personas, media, own.edits.personas, cardModifier) }
            item(key = "ignored") { IgnoredUsersCard(own.onOpenIgnoredUsers, cardModifier) }
            item(key = "account") { AccountCard(own.account, own.connection, own.edits.logout, cardModifier) }
        }
    }
}

/** Banner, avatar over its lower edge, name and Matrix ID (tap to copy). */
@Composable
private fun HeroCard(
    profile: UserProfile,
    media: ProfileMedia,
    edits: ProfileEdits?,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profile.displayName ?: profile.userId
    var editingName by rememberSaveable { mutableStateOf(false) }
    ScreenCard(modifier) {
        Column {
            Box(Modifier.fillMaxWidth().height(BANNER_HEIGHT + AVATAR_SIZE / 2)) {
                ImageSlot(
                    mxc = profile.bannerMxc,
                    title = stringResource(R.string.banner),
                    viewer = { media.viewer(profile.bannerMxc, name) },
                    onOpenMedia = onOpenMedia,
                    onChange = edits?.setBanner,
                    modifier = Modifier.fillMaxWidth().height(BANNER_HEIGHT),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = ScreenCards.Radius, topEnd = ScreenCards.Radius))
                            .background(senderColor(profile.userId).copy(alpha = BANNER_TINT)),
                    ) {
                        profile.bannerMxc?.let {
                            AsyncImage(media.full(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                ImageSlot(
                    mxc = profile.avatarMxc,
                    title = stringResource(R.string.avatar),
                    viewer = { media.viewer(profile.avatarMxc, name) },
                    onOpenMedia = onOpenMedia,
                    onChange = edits?.setAvatar,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                ) {
                    RoomAvatar(name, profile.userId, media.thumbnail(profile.avatarMxc), size = AVATAR_SIZE)
                }
            }
            NameBlock(profile, name, onEditName = edits?.let { { editingName = true } })
        }
    }
    if (editingName && edits != null) {
        TextDialog(
            title = stringResource(R.string.display_name),
            initial = profile.displayName.orEmpty(),
            singleLine = true,
            onSave = edits.setDisplayName,
            onDismiss = { editingName = false },
        )
    }
}

@Composable
private fun NameBlock(
    profile: UserProfile,
    name: String,
    onEditName: (() -> Unit)?,
) {
    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onEditName != null) {
                IconButton(onClick = onEditName) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit))
                }
            }
        }
        Text(
            profile.userId,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    clipboard.setText(AnnotatedString(profile.userId))
                },
        )
    }
}

private val BANNER_HEIGHT = 140.dp
private val AVATAR_SIZE = 96.dp
private const val BANNER_TINT = 0.35f
