package pt.aguiarvieira.xmuks.core.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * The notification's actions, reply and mark as read, sent to [receiver]: each app runs them its
 * own way (the phone through its outbox and room sessions, the watch straight to gomuks).
 */
class Actions(
    private val receiver: Class<out BroadcastReceiver>,
) {
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
            Intent(context, receiver)
                .setAction(action)
                .putExtra(ROOM_ID, roomId)
                .putExtra(EVENT_ID, eventId)
        // A reply's intent must be mutable: the system fills in the typed text.
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + roomId).hashCode(), intent, flags)
    }

    companion object {
        const val REPLY = "pt.aguiarvieira.xmuks.action.REPLY"
        const val MARK_READ = "pt.aguiarvieira.xmuks.action.MARK_READ"
        const val ROOM_ID = "room_id"
        const val EVENT_ID = "event_id"
        const val REPLY_TEXT = "reply_text"

        /** The text typed (or spoken) into a reply action, trimmed; null for none. */
        fun replyText(intent: Intent): String? =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(REPLY_TEXT)
                ?.toString()
                ?.trim()
                ?.takeUnless { it.isEmpty() }
    }
}
