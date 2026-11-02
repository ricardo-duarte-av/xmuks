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
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.RoomKeys
import javax.inject.Inject

/**
 * Runs the notification's actions. A reply joins the outbox (durable: it goes out even if we're
 * killed, and never twice) and shows in the notification at once; we stay up a few seconds for it
 * to leave. Mark as read tells gomuks and clears the notification. Both run as the notification's
 * account, whichever one is shown.
 */
@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var accounts: Accounts

    @Inject lateinit var notifiers: RoomNotifiers

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val roomId = intent.getStringExtra(Actions.ROOM_ID) ?: return
        val eventId = intent.getStringExtra(Actions.EVENT_ID)
        // None given: from before there could be several accounts, so the first one's.
        val account = intent.getStringExtra(RoomKeys.EXTRA_ACCOUNT) ?: AccountRegistry.LEGACY
        val pending = goAsync()
        scope.launch {
            try {
                val graph = accounts.graph(account)
                when (intent.action) {
                    Actions.REPLY -> Actions.replyText(intent)?.let { graph.reply(roomId, it) }
                    Actions.MARK_READ -> graph.markRead(roomId, eventId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun AccountGraph.reply(
        roomId: String,
        text: String,
    ) {
        notifiers.of(this).replied(roomId, text)
        roomSessions.open(roomId).writer.send(text)
        // Give it the chance to go out before the process may be let go (the outbox retries anyway).
        withTimeoutOrNull(SEND_WAIT_MS) { outbox.observe(roomId).first { it.isEmpty() } }
    }

    private suspend fun AccountGraph.markRead(
        roomId: String,
        eventId: String?,
    ) {
        notifiers.of(this).clear(roomId)
        if (eventId != null) roomSessions.open(roomId).writer.markRead(eventId)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Under the ~10 s a broadcast may run. */
        const val SEND_WAIT_MS = 8_000L
    }
}
