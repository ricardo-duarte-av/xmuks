package pt.aguiarvieira.xmuks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.call.system.CallService
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.designsystem.component.LocalScreenStyle
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenStyle
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import pt.aguiarvieira.xmuks.core.notify.PeopleUris
import pt.aguiarvieira.xmuks.core.notify.RoomKeys
import pt.aguiarvieira.xmuks.feature.login.LoginRoute
import pt.aguiarvieira.xmuks.feature.roomlist.HomeTab
import pt.aguiarvieira.xmuks.feature.share.ShareRequest
import pt.aguiarvieira.xmuks.navigation.CallRequest
import pt.aguiarvieira.xmuks.navigation.XmuksNavHost
import javax.inject.Inject
import javax.inject.Provider

private val CONTACT_ROWS = setOf(PeopleUris.MIME_MESSAGE, PeopleUris.MIME_VOICE_CALL, PeopleUris.MIME_VIDEO_CALL)

/** How long the splash screen waits for the screen style before going flat. */
private const val STYLE_WAIT_MS = 500L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var session: SessionRepository

    @Inject lateinit var accounts: Accounts

    // The account shown's, when asked for: another account may be shown by then.
    @Inject lateinit var incoming: Provider<IncomingCalls>

    @Inject lateinit var calls: Provider<CallManager>

    @Inject lateinit var contacts: Provider<Contacts>

    @Inject lateinit var preferences: Provider<PreferenceStore>

    private val accountViewModels: AccountViewModelStores by viewModels()

    /** Flat or cards (a setting); null until read, the splash screen staying up meanwhile. */
    private val screenStyle = MutableStateFlow<ScreenStyle?>(null)

    /** A Matrix link we were opened with (another app, or one of our notifications), until handled. */
    private val link = MutableStateFlow<String?>(null)

    /** A call screen to show (a call notification was tapped, or a ring answered), until handled. */
    private val call = MutableStateFlow<CallRequest?>(null)

    /** Something shared to us from another app, until handled. */
    private val share = MutableStateFlow<ShareRequest?>(null)

    /** Back to the room list (an account's shortcut was tapped), until handled. */
    private val home = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Kept up until the screen style is known, so a cards user never sees a flat first frame.
        installSplashScreen().setKeepOnScreenCondition { screenStyle.value == null }
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.let(::take)
        lifecycleScope.launch {
            accounts.active.flatMapLatest { preferences.get().value(Prefs.screenCards) }.collect { cards ->
                screenStyle.value = if (cards) ScreenStyle.Cards else ScreenStyle.Flat
            }
        }
        // Should reading it ever stall, the splash screen doesn't wait for it forever.
        lifecycleScope.launch {
            delay(STYLE_WAIT_MS)
            screenStyle.compareAndSet(null, ScreenStyle.Flat)
        }
        // While in a call, the app (the call screen) may show over the lock screen, as a phone's does.
        lifecycleScope.launch {
            accounts.active.flatMapLatest { calls.get().active }.collect { active ->
                setShowWhenLocked(active != null)
                setTurnScreenOn(active != null)
            }
        }
        setContent {
            val style by screenStyle.collectAsStateWithLifecycle()
            XmuksTheme {
                CompositionLocalProvider(LocalScreenStyle provides (style ?: ScreenStyle.Flat)) { Content() }
            }
        }
    }

    @Composable
    private fun Content() {
        // Another account shown is a fresh start of the app's screens, for it (none: signing in).
        val account by accounts.active.collectAsStateWithLifecycle()
        val order by accounts.registry.ids.collectAsStateWithLifecycle()
        val shift = with(LocalDensity.current) { SHARED_AXIS_SHIFT.roundToPx() }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        // Kept across accounts: switching shows the other's same tab (as its swiped preview did).
        var homeTab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
        AnimatedContent(
            targetState = account,
            transitionSpec = {
                // Between two signed-in accounts: along their order, as the header's swipe goes.
                // Signing in or out: the usual fade.
                val from = order.indexOf(initialState)
                val to = order.indexOf(targetState)
                if (accounts.swipe == initialState to targetState) {
                    // Swiped: the old screen already shows this one's preview; it takes over on top.
                    (fadeIn(tween(SWIPE_HANDOVER_MS)) togetherWith fadeOut(tween(1, delayMillis = SWIPE_HANDOVER_MS)))
                        .apply { targetContentZIndex = 1f }
                } else if (from < 0 || to < 0) {
                    (
                        fadeIn(tween(FADE_MS, delayMillis = FADE_OUT_MS)) +
                            scaleIn(tween(FADE_MS, delayMillis = FADE_OUT_MS), initialScale = FADE_SCALE)
                    ).togetherWith(fadeOut(tween(FADE_OUT_MS)))
                } else {
                    sharedAxisX(forward = (to > from) != rtl, shift)
                }
            },
            label = "account",
        ) { shown ->
            val pending by link.collectAsStateWithLifecycle()
            val shared by share.collectAsStateWithLifecycle()
            val callRoom by call.collectAsStateWithLifecycle()
            val toHome by home.collectAsStateWithLifecycle()
            if (shown != null) {
                AccountViewModels(shown, accountViewModels, this@MainActivity) {
                    XmuksNavHost(
                        link = pending,
                        onLinkConsume = { link.value = null },
                        share = shared,
                        onShareConsume = { share.value = null },
                        openCall = callRoom,
                        onOpenCallConsume = { call.value = null },
                        showHome = toHome,
                        onShowHomeConsume = { home.value = false },
                        isLocalServer = { session.isLocalServer() },
                        startTab = homeTab,
                        onHomeTabChange = { homeTab = it },
                    )
                }
            } else {
                LoginRoute()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /**
     * What [intent] asks for, as the account it's for, shown first: one of our notifications,
     * shortcuts or call screens says which; a room link from elsewhere goes to an account in the
     * room. Anything else is for the one shown.
     */
    private fun take(intent: Intent) {
        val account =
            intent.getStringExtra(RoomKeys.EXTRA_ACCOUNT)
                // Shared to a conversation shortcut: its ID says whose (none: the first account's).
                ?: intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)?.let {
                    RoomKeys.accountOf(it) ?: AccountRegistry.LEGACY
                }
        lifecycleScope.launch {
            val link = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && account == null }
            show(account ?: link?.let { accounts.accountForLink(it.toString()) })
            handle(intent)
        }
    }

    /** Shows [account] (signed in), if it isn't already. */
    private suspend fun show(account: String?) {
        if (account != null && account != accounts.active.value) accounts.registry.activate(account)
    }

    /** A Matrix link to open, a call to show, or something shared to send. */
    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(RoomKeys.EXTRA_HOME, false)) {
            home.value = true
            return
        }
        intent.getStringExtra(CallService.EXTRA_OPEN_CALL)?.let { roomId ->
            val answer = intent.getBooleanExtra(CallService.EXTRA_ANSWER, false)
            if (answer) incoming.get().answer(roomId)
            call.value = CallRequest(roomId, intent.getBooleanExtra(CallService.EXTRA_VIDEO, false), answer)
            return
        }
        if (intent.type in CONTACT_ROWS) {
            openContactRow(intent)
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> share.value = shareOf(intent)
            else -> intent.data?.let { link.value = it.toString() }
        }
    }

    /**
     * A tap on one of our rows in the phone's Contacts: Message opens the DM, Voice / Video call
     * calls in it. With no DM (any more), the person's profile.
     */
    private fun openContactRow(intent: Intent) {
        val row = intent.data ?: return
        val type = intent.type
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { column(row, ContactsContract.Data.DATA1) } ?: return@launch
            val userId = PeopleUris.userOf(uri) ?: return@launch
            // The account whose contact it is: its Android account is named after our user.
            val owner = withContext(Dispatchers.IO) { column(row, ContactsContract.RawContacts.ACCOUNT_NAME) }
            show(owner?.let { accounts.accountOfUser(it) })
            val room = contacts.get().directRoom(userId)
            when {
                room == null -> link.value = uri
                type == PeopleUris.MIME_MESSAGE -> link.value = "matrix:roomid/${room.removePrefix("!")}"
                else -> call.value = CallRequest(room, video = type == PeopleUris.MIME_VIDEO_CALL, answer = false)
            }
        }
    }

    /** [row]'s [column], if it can be read. */
    private fun column(
        row: Uri,
        column: String,
    ): String? =
        runCatching {
            contentResolver.query(row, arrayOf(column), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun shareOf(intent: Intent): ShareRequest? {
        val uris =
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (uris.isEmpty() && text.isNullOrBlank()) return null
        // Shared to one of our conversation shortcuts: its ID is the room's key.
        val room = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)?.let(RoomKeys::roomOf)
        return ShareRequest(uris.map(Uri::toString), text, room)
    }
}

/** Material's shared axis (X): the old screen slides a little and fades, the new one comes in after it. */
private fun sharedAxisX(
    forward: Boolean,
    shift: Int,
): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(SHARED_AXIS_MS)) { sign * shift } + fadeIn(tween(FADE_MS, FADE_OUT_MS))
    val exit = slideOutHorizontally(tween(SHARED_AXIS_MS)) { -sign * shift } + fadeOut(tween(FADE_OUT_MS))
    return enter togetherWith exit
}

/** AnimatedContent's own default, kept for signing in and out. */
private const val FADE_MS = 220
private const val FADE_OUT_MS = 90
private const val FADE_SCALE = 0.92f
private const val SHARED_AXIS_MS = 300

/** A swiped account's screen fading in over its preview (hiding the moment its list loads). */
private const val SWIPE_HANDOVER_MS = 150
private val SHARED_AXIS_SHIFT = 30.dp
