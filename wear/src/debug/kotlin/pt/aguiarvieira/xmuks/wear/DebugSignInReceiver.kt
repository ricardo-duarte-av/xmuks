package pt.aguiarvieira.xmuks.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.LoginHandoff
import javax.inject.Inject

/**
 * Debug builds only: signs in without a phone, for emulators.
 * `adb shell am broadcast -a pt.aguiarvieira.xmuks.wear.DEBUG_SIGN_IN -p pt.aguiarvieira.xmuks
 *  --es server https://… --es user … --es password …` (no extras: sign out).
 */
@AndroidEntryPoint
class DebugSignInReceiver : BroadcastReceiver() {
    @Inject lateinit var account: WatchAccount

    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val server = intent.getStringExtra("server")
        val user = intent.getStringExtra("user")
        val password = intent.getStringExtra("password")
        val pending = goAsync()
        scope.launch {
            try {
                if (server == null || user == null || password == null) {
                    account.signOut()
                    Log.i(TAG, "Signed out")
                } else {
                    Log.i(TAG, "Sign in: ${account.logIn(LoginHandoff(server, user, password))}")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "xmuks-wear"
    }
}
