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
import pt.aguiarvieira.xmuks.core.call.AccountCalls
import pt.aguiarvieira.xmuks.core.call.IncomingCall
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.notify.PushCodec
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.PushPayload
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcSignals
import javax.inject.Inject

/**
 * Where gomuks' pushes land (through the gateway and FCM): decrypted with the key of the account
 * they're for, and shown as it. A new FCM token (from registering, or a refresh) is registered
 * with every account's gomuks straight away.
 */
@AndroidEntryPoint
class XmuksMessagingService : FirebaseMessagingService() {
    @Inject lateinit var accounts: Accounts

    @Inject lateinit var notifiers: RoomNotifiers

    @Inject lateinit var calls: AccountCalls

    override fun onRegistered(token: String) {
        scope.launch { tokenReceived(accounts.signedIn().map { it.pushRegistrar }, token) }
    }

    /** Still how a refreshed token arrives on this Firebase version (see [FirebaseTokens]). */
    @Deprecated("Firebase moves to onRegistered; kept while it still delivers tokens here")
    override fun onNewToken(token: String) {
        scope.launch { tokenReceived(accounts.signedIn().map { it.pushRegistrar }, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val sealed = message.data[PAYLOAD] ?: return
        // FCM gives this call its own thread and a few seconds: done in place, then it may let us go.
        runBlocking {
            val (graph, opened) = open(sealed) ?: return@runBlocking
            // A ring rings (full screen, ringtone) instead of showing as a message.
            val (rings, messages) = opened.messages.partition { it.rtc?.ring == true }
            rings.forEach { calls.incoming(graph).ring(it.toIncomingCall()) }
            val payload = opened.copy(messages = messages)
            // A room is on screen only in the account shown.
            val onScreen =
                graph.openRoom.roomId.value
                    .takeIf { graph.isActive.value }
            notifiers.of(graph).show(payload, onScreen)
            // Rooms we hold in memory catch up now, while the process is alive: tapping the
            // notification then opens a current timeline. Bounded, so FCM's window isn't overrun.
            withTimeoutOrNull(PREFETCH_MS) {
                payload.messages
                    .map { it.roomId }
                    .distinct()
                    .forEach { graph.timelines.prefetch(it) }
            }
        }
    }

    /**
     * The push opened, with the account it's for: each account's gomuks encrypts with that
     * account's own key, and only the right key opens it (AES-GCM is authenticated). Its user isn't
     * enough to tell: two gomuks could be the same Matrix user.
     */
    private suspend fun open(sealed: String): Pair<AccountGraph, PushPayload>? =
        PushCodec.openWithAny(
            sealed,
            accounts.signedIn().mapNotNull { graph -> graph.pushRegistrar.pushKey()?.let { graph to it } },
        )

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
