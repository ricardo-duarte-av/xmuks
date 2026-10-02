package pt.aguiarvieira.xmuks.wear

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.notify.PushCodec
import javax.inject.Inject

/**
 * gomuks' pushes, straight to the watch over its own connection. Opened here and handed to the
 * [Holdback] at once: FCM delivers one push at a time, so waiting in here would also hold up the
 * dismissal that's meant to cancel the message.
 */
@AndroidEntryPoint
class WatchMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var holdback: Holdback

    @Inject lateinit var scope: CoroutineScope

    override fun onRegistered(token: String) {
        scope.launch { runCatching { registrar.tokenReceived(token) } }
    }

    @Deprecated("Firebase moves to onRegistered; kept while it still delivers tokens here")
    override fun onNewToken(token: String) {
        scope.launch { runCatching { registrar.tokenReceived(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val sealed = message.data[PAYLOAD] ?: return
        val payload =
            runBlocking {
                val key = registrar.pushKey() ?: return@runBlocking null
                PushCodec.open(sealed, key)
            } ?: return
        Log.d(TAG, "Push: ${payload.messages.size} message(s), dismiss ${payload.dismiss.map { it.roomId to it.ts }}")
        holdback.receive(payload)
    }

    private companion object {
        const val PAYLOAD = "payload"
        const val TAG = "xmuks-wear"
    }
}
