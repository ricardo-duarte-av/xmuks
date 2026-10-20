package pt.aguiarvieira.xmuks.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import javax.inject.Inject

/** Signed in, the past notifications (the inbox); signed out, a button to sign in from the phone. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var account: WatchAccount

    @Inject lateinit var inbox: Inbox

    @Inject lateinit var images: ImageLoader

    @Inject lateinit var store: CredentialStore

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Signed in or out on the account screen meanwhile: looked at again on every return. */
    private var signedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        signedIn = account.signedInAs() != null
        setContent {
            MaterialTheme {
                AppScaffold {
                    if (signedIn) {
                        InboxScreen(
                            inbox,
                            images,
                            store.credentials()?.serverUrl,
                            onOpen = { startActivity(MessageActivity.intent(this@MainActivity, it.message.eventId)) },
                            onAccount = { startActivity(Intent(this@MainActivity, AccountActivity::class.java)) },
                        )
                    } else {
                        AccountScreen(account, onChange = { signedIn = account.signedInAs() != null })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        signedIn = account.signedInAs() != null
    }
}

/** Who the watch is signed in as, and signing out. */
@AndroidEntryPoint
class AccountActivity : ComponentActivity() {
    @Inject lateinit var account: WatchAccount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScaffold { AccountScreen(account, onChange = ::finish) } } }
    }
}

@Composable
private fun AccountScreen(
    account: WatchAccount,
    onChange: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(account.signedInAs()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<WatchAccount.SignIn?>(null) }
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val text =
                when {
                    busy -> stringResource(R.string.signing_in)
                    user != null -> stringResource(R.string.signed_in_as, user.orEmpty())
                    else -> problem?.let { stringResource(it.message()) } ?: stringResource(R.string.signed_out)
                }
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            when {
                busy -> {
                    CircularProgressIndicator()
                }

                user != null -> {
                    Button(onClick = {
                        scope.launch {
                            account.signOut()
                            user = null
                            onChange()
                        }
                    }) { Text(stringResource(R.string.sign_out)) }
                }

                else -> {
                    Button(onClick = {
                        busy = true
                        scope.launch {
                            val result = account.signInFromPhone()
                            problem = result.takeUnless { it == WatchAccount.SignIn.Ok }
                            user = account.signedInAs()
                            busy = false
                            if (user != null) onChange()
                        }
                    }) { Text(stringResource(R.string.sign_in)) }
                }
            }
        }
    }
}

private fun WatchAccount.SignIn.message(): Int =
    when (this) {
        WatchAccount.SignIn.Ok -> R.string.signed_out
        WatchAccount.SignIn.NoPhone -> R.string.no_phone
        WatchAccount.SignIn.PhoneSignedOut -> R.string.phone_signed_out
        WatchAccount.SignIn.LoginFailed -> R.string.login_failed
        WatchAccount.SignIn.NoNetwork -> R.string.no_network
    }
