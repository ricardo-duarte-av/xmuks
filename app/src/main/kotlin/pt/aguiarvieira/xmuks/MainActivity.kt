package pt.aguiarvieira.xmuks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.call.system.CallService
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import pt.aguiarvieira.xmuks.feature.login.LoginRoute
import pt.aguiarvieira.xmuks.feature.share.ShareRequest
import pt.aguiarvieira.xmuks.navigation.CallRequest
import pt.aguiarvieira.xmuks.navigation.XmuksNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var session: SessionRepository

    /** Created with the app, so rings arriving over the stream ring while it runs. */
    @Inject lateinit var incoming: IncomingCalls

    @Inject lateinit var calls: CallManager

    /** A Matrix link we were opened with (another app, or one of our notifications), until handled. */
    private val link = MutableStateFlow<String?>(null)

    /** A call screen to show (a call notification was tapped, or a ring answered), until handled. */
    private val call = MutableStateFlow<CallRequest?>(null)

    /** Something shared to us from another app, until handled. */
    private val share = MutableStateFlow<ShareRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.let(::take)
        // While in a call, the app (the call screen) may show over the lock screen, as a phone's does.
        lifecycleScope.launch {
            calls.active.collect { active ->
                setShowWhenLocked(active != null)
                setTurnScreenOn(active != null)
            }
        }
        setContent {
            XmuksTheme {
                val loggedIn by session.loggedIn.collectAsStateWithLifecycle()
                AnimatedContent(targetState = loggedIn, label = "session") { signedIn ->
                    val pending by link.collectAsStateWithLifecycle()
                    val shared by share.collectAsStateWithLifecycle()
                    val callRoom by call.collectAsStateWithLifecycle()
                    if (signedIn) {
                        XmuksNavHost(
                            link = pending,
                            onLinkConsume = { link.value = null },
                            share = shared,
                            onShareConsume = { share.value = null },
                            openCall = callRoom,
                            onOpenCallConsume = { call.value = null },
                        )
                    } else {
                        LoginRoute()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /** A Matrix link to open, or something shared to send. */
    private fun take(intent: Intent) {
        intent.getStringExtra(CallService.EXTRA_OPEN_CALL)?.let { roomId ->
            val answer = intent.getBooleanExtra(CallService.EXTRA_ANSWER, false)
            if (answer) incoming.answer(roomId)
            call.value = CallRequest(roomId, intent.getBooleanExtra(CallService.EXTRA_VIDEO, false), answer)
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> share.value = shareOf(intent)
            else -> intent.data?.let { link.value = it.toString() }
        }
    }

    private fun shareOf(intent: Intent): ShareRequest? {
        val uris =
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (uris.isEmpty() && text.isNullOrBlank()) return null
        // Shared to one of our conversation shortcuts: its ID is the room's.
        val room = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        return ShareRequest(uris.map(Uri::toString), text, room)
    }
}
