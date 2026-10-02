package pt.aguiarvieira.xmuks.core.push

import com.google.firebase.messaging.FirebaseMessaging
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import javax.inject.Provider

/**
 * Gets the FCM token to [PushRegistrar.tokenReceived]. Firebase 25.1 moves to `register()` +
 * `onRegistered`, but that doesn't hand over the token an app already has (seen on-device), so the
 * current one is also asked for directly.
 */
class FirebaseTokens(
    private val registrar: Provider<PushRegistrar>,
    private val onToken: (suspend () -> Unit) -> Unit,
) : PushTokenSource {
    override fun request() {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.register()
            @Suppress("DEPRECATION") // see above: register() alone never delivered a token
            messaging.token.addOnSuccessListener { token -> onToken { registrar.get().tokenReceived(token) } }
        }
    }
}
