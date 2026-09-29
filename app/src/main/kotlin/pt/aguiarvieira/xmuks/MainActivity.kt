package pt.aguiarvieira.xmuks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import pt.aguiarvieira.xmuks.feature.login.LoginRoute
import pt.aguiarvieira.xmuks.navigation.XmuksNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var session: SessionRepository

    /** A Matrix link we were opened with (another app, or one of our notifications), until handled. */
    private val link = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) link.value = intent?.data?.toString()
        setContent {
            XmuksTheme {
                val loggedIn by session.loggedIn.collectAsStateWithLifecycle()
                AnimatedContent(targetState = loggedIn, label = "session") { signedIn ->
                    val pending by link.collectAsStateWithLifecycle()
                    if (signedIn) XmuksNavHost(link = pending, onLinkConsume = { link.value = null }) else LoginRoute()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { link.value = it.toString() }
    }
}
