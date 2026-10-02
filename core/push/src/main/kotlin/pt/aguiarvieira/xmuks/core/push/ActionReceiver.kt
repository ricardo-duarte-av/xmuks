package pt.aguiarvieira.xmuks.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import javax.inject.Inject

/**
 * Runs the notification's actions. A reply joins the outbox (durable: it goes out even if we're
 * killed, and never twice) and shows in the notification at once; we stay up a few seconds for it
 * to leave. Mark as read tells gomuks and clears the notification.
 */
@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var sessions: RoomSessions

    @Inject lateinit var outbox: Outbox

    @Inject lateinit var notifier: RoomNotifier

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val roomId = intent.getStringExtra(Actions.ROOM_ID) ?: return
        val eventId = intent.getStringExtra(Actions.EVENT_ID)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    Actions.REPLY -> Actions.replyText(intent)?.let { reply(roomId, it) }
                    Actions.MARK_READ -> markRead(roomId, eventId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(
        roomId: String,
        text: String,
    ) {
        notifier.replied(roomId, text)
        sessions.open(roomId).writer.send(text)
        // Give it the chance to go out before the process may be let go (the outbox retries anyway).
        withTimeoutOrNull(SEND_WAIT_MS) { outbox.observe(roomId).first { it.isEmpty() } }
    }

    private suspend fun markRead(
        roomId: String,
        eventId: String?,
    ) {
        notifier.clear(roomId)
        if (eventId != null) sessions.open(roomId).writer.markRead(eventId)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Under the ~10 s a broadcast may run. */
        const val SEND_WAIT_MS = 8_000L
    }
}
