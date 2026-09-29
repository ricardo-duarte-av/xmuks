package pt.aguiarvieira.xmuks.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.data.push.PushRegistrar
import javax.inject.Inject

/**
 * Where gomuks' pushes land (through the gateway and FCM): decrypted with our key and shown. A
 * new FCM token (from registering, or a refresh) is registered with gomuks straight away.
 */
@AndroidEntryPoint
class XmuksMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var notifier: RoomNotifier

    @Inject lateinit var openRoom: OpenRoom

    override fun onRegistered(token: String) {
        scope.launch { runCatching { registrar.tokenReceived(token) } }
    }

    /** Still how a refreshed token arrives on this Firebase version (see [FirebaseTokens]). */
    @Deprecated("Firebase moves to onRegistered; kept while it still delivers tokens here")
    override fun onNewToken(token: String) {
        scope.launch { runCatching { registrar.tokenReceived(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val sealed = message.data[PAYLOAD] ?: return
        // FCM gives this call its own thread and a few seconds: done in place, then it may let us go.
        runBlocking {
            val key = registrar.pushKey() ?: return@runBlocking
            val payload = PushCodec.open(sealed, key) ?: return@runBlocking
            notifier.show(payload, openRoom.roomId.value)
        }
    }

    private companion object {
        const val PAYLOAD = "payload"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
