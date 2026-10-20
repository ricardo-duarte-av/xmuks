package pt.aguiarvieira.xmuks.wear

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import javax.inject.Inject
import javax.inject.Singleton

/** What both a notification's actions and the watch's own screens do to a room. */
@Singleton
class WatchActions
    @Inject
    constructor(
        private val exec: ExecClient,
        private val notifier: RoomNotifier,
        private val account: WatchAccount,
    ) {
        /** Marks [roomId] read up to [eventId] (when known); its notification goes either way. */
        suspend fun markRead(
            roomId: String,
            eventId: String?,
        ): Boolean {
            notifier.clear(roomId)
            if (eventId == null) return true
            val params =
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("event_id", JsonPrimitive(eventId))
                    put("receipt_type", JsonPrimitive(account.receiptType()))
                }
            return exec.exec("mark_read", params, ExecMode.Write) is ExecResult.Ok
        }
    }
