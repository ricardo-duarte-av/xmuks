package pt.aguiarvieira.xmuks.core.data.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * The account's push rules, all of them, and changing them (`update_push_rule`). Keywords are
 * content rules of our own; a room's keyword is an override rule matching the room and the word,
 * and silencing one of the account's keywords in a room is the same rule with no actions.
 */
class PushRuleEditor(
    database: XmuksDatabase,
    private val exec: ExecClient,
) {
    private val dao = database.roomListDao()

    /** What each changed rule should be (null: gone), until gomuks' synced copy says the same. */
    private val pending = MutableStateFlow<Map<Pair<PushRuleKind, String>, PushRule?>>(emptyMap())

    val ruleset: Flow<PushRuleset> =
        combine(
            dao.accountData("", RoomPushRules.PUSH_RULES).map { json -> PushRuleset.of(json?.let(::parse)) },
            pending,
        ) { synced, edits ->
            // Caught up: the synced rule is the truth again, and a change made elsewhere later shows.
            val caughtUp = edits.filter { (key, rule) -> synced.find(key.first, key.second) == rule }.keys
            if (caughtUp.isNotEmpty()) pending.update { it - caughtUp }
            synced.with(edits - caughtUp)
        }.distinctUntilChanged()

    /** Switches a rule on or off; null when done, else why not. */
    suspend fun setEnabled(
        rule: PushRule,
        enabled: Boolean,
    ): String? =
        change(rule.kind, rule.ruleId, { it?.copy(enabled = enabled) }) {
            update(rule.kind, rule.ruleId, if (enabled) "enable" else "disable")
        }

    /** Replaces a rule's actions: the only change the server's own rules take. */
    suspend fun setActions(
        rule: PushRule,
        actions: JsonArray,
    ): String? =
        change(rule.kind, rule.ruleId, { it?.copy(actions = actions) }) {
            update(rule.kind, rule.ruleId, "put_actions", "actions" to actions)
        }

    /**
     * Creates or replaces a rule of our own with [content] (`actions`, and `conditions` or
     * `pattern` as its kind needs); it goes first among ours of its kind.
     */
    suspend fun put(
        kind: PushRuleKind,
        ruleId: String,
        content: JsonObject,
    ): String? {
        val rule =
            PushRule(
                kind = kind,
                ruleId = ruleId,
                actions = content["actions"] as? JsonArray ?: JsonArray(emptyList()),
                conditions = content["conditions"] as? JsonArray ?: JsonArray(emptyList()),
                pattern = (content["pattern"] as? JsonPrimitive)?.content?.takeIf { kind == PushRuleKind.Content },
            )
        val sent =
            buildJsonObject {
                put("actions", rule.actions)
                if (rule.conditions.isNotEmpty()) put("conditions", rule.conditions)
                rule.pattern?.let { put("pattern", JsonPrimitive(it)) }
            }
        return change(kind, ruleId, { rule }) { update(kind, ruleId, "put", "new_content" to sent) }
    }

    /** Deletes a rule of our own; one already gone counts as done. */
    suspend fun delete(
        kind: PushRuleKind,
        ruleId: String,
    ): String? = change(kind, ruleId, { null }) { update(kind, ruleId, "delete")?.takeUnless { NOT_FOUND in it } }

    /** A word that notifies and highlights wherever it's written. */
    suspend fun addKeyword(word: String): String? {
        val clean = word.trim().takeIf { it.isNotEmpty() } ?: return null
        val content =
            buildJsonObject {
                put("actions", NotifyPreset.Highlight.actions())
                put("pattern", JsonPrimitive(clean))
            }
        return put(PushRuleKind.Content, clean, content)
    }

    suspend fun removeKeyword(keyword: Keyword): String? = delete(PushRuleKind.Content, keyword.ruleId)

    /** A word that notifies and highlights in [roomId] only. */
    suspend fun addRoomKeyword(
        roomId: String,
        word: String,
    ): String? {
        val clean = word.trim().takeIf { it.isNotEmpty() } ?: return null
        return put(
            PushRuleKind.Override,
            roomKeywordId(roomId, clean),
            roomKeyword(roomId, clean, NotifyPreset.Highlight)
        )
    }

    /** Takes one of [roomId]'s own keywords away, or lets one of the account's notify there again. */
    suspend fun removeRoomKeyword(
        roomId: String,
        word: String,
    ): String? {
        val id =
            ruleset
                .first()[PushRuleKind.Override]
                .firstOrNull { PushRuleset.roomWord(it, roomId).equals(word, ignoreCase = true) }
                ?.ruleId ?: roomKeywordId(roomId, word)
        return delete(PushRuleKind.Override, id)
    }

    /**
     * Silences one of the account's keywords in [roomId]: an override that matches it there and does
     * nothing. Overrides come first, so a message with the word there doesn't notify, even one that
     * also mentions us.
     */
    suspend fun silenceHere(
        roomId: String,
        word: String,
    ): String? = put(PushRuleKind.Override, roomKeywordId(roomId, word), roomKeyword(roomId, word, NotifyPreset.Off))

    /** Sets what [rule] at [kind] becomes before telling gomuks; undone if gomuks says no. */
    private suspend fun change(
        kind: PushRuleKind,
        ruleId: String,
        becomes: (PushRule?) -> PushRule?,
        send: suspend () -> String?,
    ): String? {
        val key = kind to ruleId
        val before = pending.value
        val current = if (key in before) before[key] else ruleset.first().find(kind, ruleId)
        pending.update { it + (key to becomes(current)) }
        return send().also { error -> if (error != null) pending.update { it - key } }
    }

    private suspend fun update(
        kind: PushRuleKind,
        ruleId: String,
        action: String,
        extra: Pair<String, JsonElement>? = null,
    ): String? {
        val params =
            buildJsonObject {
                put("kind", JsonPrimitive(kind.wire))
                put("rule_id", JsonPrimitive(ruleId))
                put("action", JsonPrimitive(action))
                extra?.let { (name, value) -> put(name, value) }
            }
        return when (val result = exec.exec("update_push_rule", params, ExecMode.Write)) {
            is ExecResult.Ok -> null
            is ExecResult.CommandError -> result.message
            is ExecResult.NetworkError -> result.cause.message ?: "network error"
        }
    }

    private fun parse(json: String): JsonObject? =
        runCatching {
            GomuksJson.parseToJsonElement(json) as? JsonObject
        }.getOrNull()

    companion object {
        private const val NOT_FOUND = "M_NOT_FOUND"

        /** Our ID for [word]'s rule in [roomId]: one per word and room, notifying or silencing. */
        fun roomKeywordId(
            roomId: String,
            word: String,
        ) = "xmuks.keyword.$roomId.${word.lowercase()}"

        private fun roomKeyword(
            roomId: String,
            word: String,
            preset: NotifyPreset,
        ) = buildJsonObject {
            put("actions", preset.actions())
            put(
                "conditions",
                buildJsonArray {
                    add(match(PushRule.ROOM_ID, roomId))
                    add(match(PushRule.BODY, word))
                },
            )
        }

        private fun match(
            key: String,
            pattern: String,
        ) = buildJsonObject {
            put("kind", JsonPrimitive("event_match"))
            put("key", JsonPrimitive(key))
            put("pattern", JsonPrimitive(pattern))
        }
    }
}

/** This ruleset with [edits] made: changed rules in place, new ones first among their kind, deleted ones gone. */
internal fun PushRuleset.with(edits: Map<Pair<PushRuleKind, String>, PushRule?>): PushRuleset {
    if (edits.isEmpty()) return this
    return PushRuleset(
        PushRuleKind.entries.associateWith { kind ->
            val mine = edits.filterKeys { it.first == kind }.mapKeys { it.key.second }
            val existing = get(kind)
            val added =
                mine
                    .filter { (id, rule) ->
                        rule != null && existing.none { it.ruleId == id }
                    }.values
                    .filterNotNull()
            added + existing.mapNotNull { rule -> if (rule.ruleId in mine) mine[rule.ruleId] else rule }
        },
    )
}
