package pt.aguiarvieira.xmuks.core.push

import com.google.firebase.messaging.FirebaseMessaging
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource

/**
 * Gets the FCM token to [PushRegistrar.tokenReceived], for every signed-in account (one token for
 * the install, registered with each account's gomuks). Firebase 25.1 moves to `register()` +
 * `onRegistered`, but that doesn't hand over the token an app already has (seen on-device), so the
 * current one is also asked for directly.
 */
class FirebaseTokens(
    private val registrars: () -> List<PushRegistrar>,
    private val onToken: (suspend () -> Unit) -> Unit,
) : PushTokenSource {
    override fun request() {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.register()
            @Suppress("DEPRECATION") // see above: register() alone never delivered a token
            messaging.token.addOnSuccessListener { token -> onToken { tokenReceived(registrars(), token) } }
        }
    }
}

/** [token] registered with every account's gomuks: one failing doesn't keep it from the others. */
suspend fun tokenReceived(
    registrars: List<PushRegistrar>,
    token: String,
) = registrars.forEach { runCatching { it.tokenReceived(token) } }
