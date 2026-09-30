package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** What a room's long-press menu shows: its tags, and whether it's muted. */
data class RoomMenuState(
    val favourite: Boolean = false,
    val lowPriority: Boolean = false,
    val muted: Boolean = false,
)

/**
 * What can be done to a room from the room list, as gomuks web's room menu does it: favourite and
 * low priority (`m.tag` room account data), mute (a room push rule that doesn't notify: mentions and
 * keywords still do), mark read (up to the event the list previews), and a home-screen shortcut.
 */
class RoomListActions(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val pushRules: RoomPushRules,
    private val preferences: PreferenceStore,
    private val shortcuts: RoomShortcuts,
) {
    private val dao = database.roomListDao()

    fun state(roomId: String): Flow<RoomMenuState> =
        combine(dao.accountData(roomId, TAGS).map(::tagsOf), pushRules.setting(roomId)) { tags, notifications ->
            RoomMenuState(
                favourite = FAVOURITE in tags,
                lowPriority = LOW_PRIORITY in tags,
                muted = notifications in MUTED,
            )
        }.distinctUntilChanged()

    suspend fun setFavourite(
        roomId: String,
        on: Boolean,
    ) = setTag(roomId, FAVOURITE, on)

    suspend fun setLowPriority(
        roomId: String,
        on: Boolean,
    ) = setTag(roomId, LOW_PRIORITY, on)

    /** Muted: a room rule that doesn't notify (as gomuks web's Mute). Unmuted: the account's defaults. */
    suspend fun setMuted(
        roomId: String,
        on: Boolean,
    ): Result<Unit> {
        val error = pushRules.set(roomId, if (on) RoomNotifications.MentionsAndKeywords else RoomNotifications.Default)
        return if (error == null) Result.success(Unit) else Result.failure(IllegalStateException(error))
    }

    /** Read up to the event the room list shows: a public receipt, or a private one if we don't send those. */
    suspend fun markRead(roomId: String): Result<Unit> {
        val eventId =
            dao.previewEventId(roomId) ?: return Result.failure(IllegalStateException("No event to mark read"))
        val public = preferences.layers(roomId).first().get(Prefs.sendReadReceipts)
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("receipt_type", JsonPrimitive(if (public) "m.read" else "m.read.private"))
            }
        return exec.exec("mark_read", params, ExecMode.Write).toResult().map {}
    }

    suspend fun pinShortcut(room: RoomSummary): Boolean = shortcuts.pin(room)

    /** [tag] added to, or taken from, the room's tags; the others stay as they are. */
    private suspend fun setTag(
        roomId: String,
        tag: String,
        on: Boolean,
    ): Result<Unit> {
        val current = parse(dao.accountData(roomId, TAGS).first())?.get("tags") as? JsonObject ?: JsonObject(emptyMap())
        val tags =
            if (on) {
                JsonObject(current + (tag to (current[tag] ?: JsonObject(emptyMap()))))
            } else {
                JsonObject(current - tag)
            }
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(TAGS))
                put("content", buildJsonObject { put("tags", tags) })
            }
        return exec.exec("set_account_data", params, ExecMode.Write).toResult().map {}
    }

    private fun tagsOf(json: String?): Set<String> = (parse(json)?.get("tags") as? JsonObject)?.keys.orEmpty()

    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { GomuksJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private companion object {
        const val TAGS = "m.tag"
        const val FAVOURITE = "m.favourite"
        const val LOW_PRIORITY = "m.lowpriority"
        val MUTED = setOf(RoomNotifications.MentionsAndKeywords, RoomNotifications.Off)
    }
}
