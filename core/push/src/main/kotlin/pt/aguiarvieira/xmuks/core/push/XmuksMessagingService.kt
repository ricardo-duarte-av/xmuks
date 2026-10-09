package pt.aguiarvieira.xmuks.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.call.IncomingCall
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineStore
import pt.aguiarvieira.xmuks.core.notify.PushCodec
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcSignals
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

    @Inject lateinit var timelines: TimelineStore

    @Inject lateinit var incoming: IncomingCalls

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
            val opened = PushCodec.open(sealed, key) ?: return@runBlocking
            // A ring rings (full screen, ringtone) instead of showing as a message.
            val (rings, messages) = opened.messages.partition { it.rtc?.ring == true }
            rings.forEach { incoming.ring(it.toIncomingCall()) }
            val payload = opened.copy(messages = messages)
            notifier.show(payload, openRoom.roomId.value)
            // Rooms we hold in memory catch up now, while the process is alive: tapping the
            // notification then opens a current timeline. Bounded, so FCM's window isn't overrun.
            withTimeoutOrNull(PREFETCH_MS) {
                payload.messages
                    .map { it.roomId }
                    .distinct()
                    .forEach { timelines.prefetch(it) }
            }
        }
    }

    private fun PushMessage.toIncomingCall() =
        IncomingCall(
            roomId = roomId,
            eventId = eventId,
            callerId = sender.id,
            callerName = if (isDm) roomName else sender.name,
            roomName = roomName,
            isDirect = isDm,
            video = rtc?.video == true,
            // Without a lifetime, a ring lasts as long as Element Call rings.
            expiresAt = rtc?.expiresAt ?: (timestamp + RtcSignals.RING_LIFETIME_MS),
        )

    private companion object {
        const val PAYLOAD = "payload"
        const val PREFETCH_MS = 5_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
