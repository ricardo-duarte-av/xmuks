package pt.aguiarvieira.xmuks.core.push

import android.os.SystemClock
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pt.aguiarvieira.xmuks.core.call.AccountCalls
import pt.aguiarvieira.xmuks.core.call.IncomingCall
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.notify.PushCodec
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.PushPayload
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcSignals
import java.io.File
import java.time.LocalDateTime
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
        val started = SystemClock.elapsedRealtime()
        // FCM gives this call its own thread and a few seconds: done in place, then it may let us go.
        val line =
            runBlocking {
                val (graph, opened) = open(sealed) ?: return@runBlocking "undecryptable"
                // A ring rings (full screen, ringtone) instead of showing as a message.
                val (rings, messages) = opened.messages.partition { it.rtc?.ring == true }
                rings.forEach { calls.incoming(graph).ring(it.toIncomingCall()) }
                val payload = opened.copy(messages = messages)
                // A room is on screen only in the account shown.
                val onScreen =
                    graph.openRoom.roomId.value
                        .takeIf { graph.isActive.value }
                // Nothing else: the push is the whole message. Opening the room (or the app) catches
                // it up then, so a push costs only decrypting and showing it.
                val shown = notifiers.of(graph).show(payload, onScreen)
                describe(graph.id, opened, rings.size, shown)
            }
        // What pushes cost and what they bring (the battery investigation): one line each, in
        // logcat and in a file adb can read (Android/data/…/files), since logcat forgets in an hour.
        val full = "$line bytes=${sealed.length} ms=${SystemClock.elapsedRealtime() - started}"
        Log.i(TAG, full)
        record(full)
    }

    /** Appends [line] to the push log, starting it over past [LOG_MAX_BYTES]. */
    private fun record(line: String) {
        runCatching {
            val file = File(getExternalFilesDir(null) ?: return, LOG_FILE)
            synchronized(LOG_LOCK) {
                if (file.length() > LOG_MAX_BYTES) file.delete()
                file.appendText("${LocalDateTime.now()} $line\n")
            }
        }.onFailure { Log.w(TAG, "Couldn't write the push log", it) }
    }

    /** A push in one line: whose, what it carried, and how much of it was shown. */
    private fun describe(
        account: String,
        push: PushPayload,
        rings: Int,
        shown: Int,
    ): String {
        val messages = push.messages.filter { it.rtc?.ring != true }
        val rooms =
            messages
                .groupingBy { it.roomId }
                .eachCount()
                .entries
                .joinToString(",") { "${it.key}x${it.value}" }
        val flags =
            messages.joinToString("") {
                when {
                    it.rtc != null -> "c"
                    it.mention -> "m"
                    it.sound -> "s"
                    it.image != null -> "i"
                    else -> "-"
                }
            }
        return "account=$account messages=${messages.size} shown=$shown rings=$rings " +
            "dismiss=${push.dismiss.size} invites=${push.invites.size} flags=$flags rooms=$rooms"
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
        const val TAG = "XmuksPush"
        const val LOG_FILE = "push-log.txt"
        const val LOG_MAX_BYTES = 4L * 1024 * 1024
        val LOG_LOCK = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
