package pt.aguiarvieira.xmuks.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.data.roominfo.SendBlock
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.RoomKeys
import javax.inject.Inject

/**
 * Runs the notification's actions. A reply joins the outbox (durable: it goes out even if we're
 * killed, and never twice) and shows in the notification at once; we stay up a few seconds for it
 * to leave. Mark as read tells gomuks and clears the notification. An invite's accept joins (through
 * the inviter's server) and decline leaves; its notification goes once that worked, and stays to
 * try again if it didn't. All run as the notification's account, whichever one is shown.
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
                    Actions.REPLY -> {
                        Actions.replyText(intent)?.let { graph.reply(context, roomId, it) }
                    }

                    Actions.MARK_READ -> {
                        graph.markRead(roomId, eventId)
                    }

                    Actions.ACCEPT -> {
                        graph.answerInvite(roomId, accept = true, serverOf(intent))
                    }

                    Actions.DECLINE -> {
                        graph.answerInvite(roomId, accept = false)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun AccountGraph.reply(
        context: Context,
        roomId: String,
        text: String,
    ) {
        val session = roomSessions.open(roomId)
        // As in the room: where we can't post, it isn't sent, and that's said.
        session.loadState()
        val block = session.sendBlock.first()
        if (block != null) {
            notifiers.of(this).replyRefused(roomId)
            val why =
                when (block) {
                    is SendBlock.Replaced -> {
                        context.getString(R.string.reply_room_replaced)
                    }

                    is SendBlock.NoPermission -> {
                        context.getString(
                            R.string.reply_no_permission,
                            block.needed,
                            block.mine
                        )
                    }
                }
            withContext(Dispatchers.Main) { Toast.makeText(context, why, Toast.LENGTH_LONG).show() }
            return
        }
        notifiers.of(this).replied(roomId, text)
        session.writer.send(text)
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

    /** Joins (through [via], the inviter's server) or leaves; the notification goes once it worked. */
    private suspend fun AccountGraph.answerInvite(
        roomId: String,
        accept: Boolean,
        via: String? = null,
    ) {
        val answered =
            withTimeoutOrNull(SEND_WAIT_MS) {
                if (accept) roomInfo.join(roomId, listOfNotNull(via)) else roomInfo.leave(roomId)
            }
        if (answered?.isSuccess == true) notifiers.of(this).clear(roomId)
    }

    /** The inviter's server, which is certain to be in the room. */
    private fun serverOf(intent: Intent) =
        intent
            .getStringExtra(Actions.INVITER)
            ?.substringAfter(':', "")
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Under the ~10 s a broadcast may run. */
        const val SEND_WAIT_MS = 8_000L
    }
}
