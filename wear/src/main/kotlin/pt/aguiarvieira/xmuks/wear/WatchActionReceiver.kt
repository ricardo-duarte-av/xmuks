package pt.aguiarvieira.xmuks.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import javax.inject.Inject

/**
 * Reply and mark as read, sent straight to gomuks: the watch keeps no outbox. Both are writes, so
 * [ExecClient] retries them under one transaction ID and gomuks never runs one twice.
 */
@AndroidEntryPoint
class WatchActionReceiver : BroadcastReceiver() {
    @Inject lateinit var exec: ExecClient

    @Inject lateinit var notifier: RoomNotifier

    @Inject lateinit var account: WatchAccount

    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val roomId = intent.getStringExtra(Actions.ROOM_ID) ?: return
        val eventId = intent.getStringExtra(Actions.EVENT_ID)
        Log.d(TAG, "${intent.action} in $roomId")
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
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("text", JsonPrimitive(text))
            }
        val result = exec.exec("send_message", params, ExecMode.Write)
        Log.d(TAG, "Reply sent: ${result::class.simpleName}")
    }

    private suspend fun markRead(
        roomId: String,
        eventId: String?,
    ) {
        notifier.clear(roomId)
        if (eventId == null) return
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("receipt_type", JsonPrimitive(account.receiptType()))
            }
        exec.exec("mark_read", params, ExecMode.Write)
    }

    private companion object {
        const val TAG = "xmuks-wear"
    }
}
