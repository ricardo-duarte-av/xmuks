package pt.aguiarvieira.xmuks.core.data.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * How a room notifies, as push rules (checked in order: override, content, room, sender,
 * underride — so a room rule sits below keyword rules and above the account's defaults).
 */
enum class RoomNotifications {
    /** No rule for the room: the account's settings (DMs and groups) decide. */
    Default,

    /** A room rule that notifies: every message. */
    All,

    /** A room rule that doesn't: mentions and keywords still do, their rules come first. */
    MentionsAndKeywords,

    /** An override rule matching the room: nothing at all, not even mentions. */
    Off,
}

/** Reads and changes a room's notification setting (`m.push_rules`, `update_push_rule`). */
class RoomPushRules(
    database: XmuksDatabase,
    private val exec: ExecClient,
) {
    private val dao = database.roomListDao()

    fun setting(roomId: String): Flow<RoomNotifications> =
        dao
            .accountData("", PUSH_RULES)
            .map { json -> json?.let { settingOf(parse(it), roomId) } ?: RoomNotifications.Default }
            .distinctUntilChanged()

    /**
     * Replaces whatever rule the room had with [setting]'s; null when done, else why not. Both
     * possible rules are deleted first whether or not our copy of the rules has them: gomuks'
     * copy can lag behind the server's (seen live), and a missing rule is just "not found".
     */
    suspend fun set(
        roomId: String,
        setting: RoomNotifications,
    ): String? {
        listOf(OVERRIDE, ROOM).forEach { kind ->
            update(kind, roomId, "delete")?.takeUnless { NOT_FOUND in it }?.let { return it }
        }
        return when (setting) {
            RoomNotifications.Default -> {
                null
            }

            RoomNotifications.All -> {
                update(ROOM, roomId, "put", rule(notifyActions()))
            }

            RoomNotifications.MentionsAndKeywords -> {
                update(ROOM, roomId, "put", rule(JsonArray(emptyList())))
            }

            RoomNotifications.Off -> {
                update(
                    OVERRIDE,
                    roomId,
                    "put",
                    rule(JsonArray(emptyList()), roomCondition(roomId))
                )
            }
        }
    }

    private suspend fun update(
        kind: String,
        ruleId: String,
        action: String,
        content: JsonObject? = null,
    ): String? {
        val params =
            buildJsonObject {
                put("kind", JsonPrimitive(kind))
                put("rule_id", JsonPrimitive(ruleId))
                put("action", JsonPrimitive(action))
                content?.let { put("new_content", it) }
            }
        return when (val result = exec.exec("update_push_rule", params, ExecMode.Write)) {
            is ExecResult.Ok -> null
            is ExecResult.CommandError -> result.message
            is ExecResult.NetworkError -> result.cause.message ?: "network error"
        }
    }

    private fun rule(
        actions: JsonArray,
        conditions: JsonArray? = null,
    ) = buildJsonObject {
        put("actions", actions)
        conditions?.let { put("conditions", it) }
    }

    private fun notifyActions() =
        buildJsonArray {
            add(JsonPrimitive("notify"))
            add(
                buildJsonObject {
                    put("set_tweak", JsonPrimitive("sound"))
                    put("value", JsonPrimitive("default"))
                },
            )
        }

    private fun roomCondition(roomId: String) =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("kind", JsonPrimitive("event_match"))
                    put("key", JsonPrimitive("room_id"))
                    put("pattern", JsonPrimitive(roomId))
                },
            )
        }

    private fun parse(json: String): JsonObject? =
        runCatching {
            GomuksJson.parseToJsonElement(json) as? JsonObject
        }.getOrNull()

    companion object {
        const val PUSH_RULES = "m.push_rules"
        private const val OVERRIDE = "override"
        private const val ROOM = "room"
        private const val NOT_FOUND = "M_NOT_FOUND"

        /** The room's setting in the account's push rules. */
        fun settingOf(
            pushRules: JsonObject?,
            roomId: String,
        ): RoomNotifications {
            if (pushRules.findRule(OVERRIDE, roomId)?.enabled() == true) return RoomNotifications.Off
            val room = pushRules.findRule(ROOM, roomId)?.takeIf { it.enabled() } ?: return RoomNotifications.Default
            val notifies = (room["actions"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.content == "notify" }
            return if (notifies) RoomNotifications.All else RoomNotifications.MentionsAndKeywords
        }

        private fun JsonObject?.findRule(
            kind: String,
            roomId: String,
        ): JsonObject? {
            val rules = ((this?.get("global") as? JsonObject)?.get(kind) as? JsonArray).orEmpty()
            return rules.mapNotNull { it as? JsonObject }.firstOrNull { it.str("rule_id") == roomId }
        }

        private fun JsonObject.enabled(): Boolean = (get("enabled") as? JsonPrimitive)?.booleanOrNull != false
    }
}
