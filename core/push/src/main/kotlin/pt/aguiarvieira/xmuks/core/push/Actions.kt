package pt.aguiarvieira.xmuks.core.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import javax.inject.Inject

/** The notification's actions: reply (through the outbox, never twice) and mark as read. */
internal object Actions {
    const val REPLY = "pt.aguiarvieira.xmuks.action.REPLY"
    const val MARK_READ = "pt.aguiarvieira.xmuks.action.MARK_READ"
    const val ROOM_ID = "room_id"
    const val EVENT_ID = "event_id"
    const val REPLY_TEXT = "reply_text"

    fun add(
        builder: NotificationCompat.Builder,
        context: Context,
        roomId: String,
        latestEventId: String,
    ) {
        val input = RemoteInput.Builder(REPLY_TEXT).setLabel(context.getString(R.string.reply_hint)).build()
        val reply =
            NotificationCompat.Action
                .Builder(
                    0,
                    context.getString(R.string.action_reply),
                    intent(context, REPLY, roomId, latestEventId, mutable = true)
                ).addRemoteInput(input)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        val markRead =
            NotificationCompat.Action
                .Builder(
                    0,
                    context.getString(R.string.action_mark_read),
                    intent(context, MARK_READ, roomId, latestEventId, mutable = false)
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
        builder.addAction(reply).addAction(markRead)
    }

    private fun intent(
        context: Context,
        action: String,
        roomId: String,
        eventId: String,
        mutable: Boolean,
    ): PendingIntent {
        val intent =
            Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(ROOM_ID, roomId)
                .putExtra(EVENT_ID, eventId)
        // A reply's intent must be mutable: the system fills in the typed text.
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + roomId).hashCode(), intent, flags)
    }
}

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
                    Actions.REPLY -> reply(roomId, intent)
                    Actions.MARK_READ -> markRead(roomId, eventId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(
        roomId: String,
        intent: Intent,
    ) {
        val text =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(Actions.REPLY_TEXT)
                ?.toString()
                ?.trim()
        if (text.isNullOrEmpty()) return
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
