package pt.aguiarvieira.xmuks.wear

import android.Manifest
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The watch app's only screen: signed in (and as whom), or a button to sign in from the phone. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var account: WatchAccount

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { AccountScreen(account) } }
    }
}

@Composable
private fun AccountScreen(account: WatchAccount) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(account.signedInAs()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<WatchAccount.SignIn?>(null) }
    AppScaffold {
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
                            }
                        }) { Text(stringResource(R.string.sign_in)) }
                    }
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
