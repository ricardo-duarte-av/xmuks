package pt.aguiarvieira.xmuks.core.data.push

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.bool
import pt.aguiarvieira.xmuks.core.data.timeline.str

/** The five kinds of push rule, in the order they're checked: the first rule that matches decides. */
enum class PushRuleKind(
    val wire: String,
) {
    Override("override"),
    Content("content"),
    Room("room"),
    Sender("sender"),
    Underride("underride"),
}

/** What a rule's actions come to, as the editor offers them; [Custom] is anything else, shown but not offered. */
enum class NotifyPreset {
    Off,
    Notify,
    NotifySound,
    Highlight,
    Custom,
    ;

    /** The actions for this preset ([Custom] has none of its own). */
    fun actions(): JsonArray =
        buildJsonArray {
            if (this@NotifyPreset == Off || this@NotifyPreset == Custom) return@buildJsonArray
            add(JsonPrimitive(NOTIFY))
            if (this@NotifyPreset == Highlight) add(tweak(HIGHLIGHT, JsonPrimitive(true)))
            if (this@NotifyPreset != Notify) add(tweak(SOUND, JsonPrimitive("default")))
        }

    companion object {
        /** What can be picked: every preset but [Custom]. */
        val choices = listOf(Off, Notify, NotifySound, Highlight)

        /** The preset [actions] amount to; [Custom] for tweaks or actions the presets don't have. */
        fun of(actions: JsonArray): NotifyPreset {
            var notify = false
            var sound = false
            var highlight = false
            actions.forEach { action ->
                val name = (action as? JsonPrimitive)?.contentOrNull
                val tweak = (action as? JsonObject)?.str("set_tweak")
                when {
                    name == NOTIFY || name == COALESCE -> {
                        notify = true
                    }

                    name == DONT_NOTIFY -> {
                        Unit
                    }

                    // A highlight tweak with no value means true; false is as if it weren't there.
                    tweak == HIGHLIGHT -> {
                        highlight =
                            ((action as JsonObject)["value"] as? JsonPrimitive)?.booleanOrNull != false
                    }

                    tweak == SOUND -> {
                        sound = true
                    }

                    else -> {
                        return Custom
                    }
                }
            }
            return when {
                !notify -> if (sound || highlight) Custom else Off
                highlight -> if (sound) Highlight else Custom
                sound -> NotifySound
                else -> Notify
            }
        }

        private fun tweak(
            name: String,
            value: JsonPrimitive,
        ) = buildJsonObject {
            put("set_tweak", JsonPrimitive(name))
            put("value", value)
        }

        private const val NOTIFY = "notify"
        private const val DONT_NOTIFY = "dont_notify"
        private const val COALESCE = "coalesce"
        private const val HIGHLIGHT = "highlight"
        private const val SOUND = "sound"
    }
}

/** One push rule, its actions and conditions kept as they came so nothing is lost in an edit. */
data class PushRule(
    val kind: PushRuleKind,
    val ruleId: String,
    /** The server's own rule: it can be switched off and its actions changed, never deleted or replaced. */
    val default: Boolean = false,
    val enabled: Boolean = true,
    val actions: JsonArray = JsonArray(emptyList()),
    val conditions: JsonArray = JsonArray(emptyList()),
    /** Content rules: the glob the message's body is matched against. */
    val pattern: String? = null,
) {
    val preset: NotifyPreset get() = NotifyPreset.of(actions)

    /** The room this rule is about: a room rule's ID, or an override's or underride's `room_id` condition. */
    val roomId: String?
        get() =
            when (kind) {
                PushRuleKind.Room -> {
                    ruleId.takeIf { it.startsWith("!") }
                }

                PushRuleKind.Override, PushRuleKind.Underride -> {
                    conditionList()
                        .firstOrNull {
                            it.isMatch(
                                ROOM_ID
                            )
                        }?.str("pattern")
                }

                else -> {
                    null
                }
            }

    /** The person this rule is about: a sender rule's ID. */
    val userId: String? get() = ruleId.takeIf { kind == PushRuleKind.Sender && it.startsWith("@") }

    /** What a raw edit starts from: the actions, and (for our own rules) what it matches. */
    fun editable(): JsonObject =
        buildJsonObject {
            put("actions", actions)
            if (!default) {
                if (kind == PushRuleKind.Override || kind == PushRuleKind.Underride) put("conditions", conditions)
                if (kind == PushRuleKind.Content) put("pattern", JsonPrimitive(pattern.orEmpty()))
            }
        }

    internal fun conditionList(): List<JsonObject> = conditions.mapNotNull { it as? JsonObject }

    companion object {
        internal const val ROOM_ID = "room_id"
        internal const val BODY = "content.body"
    }
}

/** An `event_match` on [key]. */
internal fun JsonObject.isMatch(key: String) = str("kind") == "event_match" && str("key") == key

/** A word that notifies (and highlights) wherever it's written: a content rule of our own. */
data class Keyword(
    val word: String,
    val ruleId: String,
    val enabled: Boolean,
)

/** A room's own keywords, and which of the account's are silenced in it. */
data class RoomKeywords(
    /** Words that notify only here (override rules matching the room and the word). */
    val own: List<String>,
    /** The account's keywords, each with whether it's silenced here. */
    val global: List<Pair<Keyword, Boolean>>,
)

/** The account's push rules (`m.push_rules`' `global`), by kind, each kind in the order it's checked. */
data class PushRuleset(
    val rules: Map<PushRuleKind, List<PushRule>> = emptyMap(),
) {
    operator fun get(kind: PushRuleKind): List<PushRule> = rules[kind].orEmpty()

    fun find(
        kind: PushRuleKind,
        ruleId: String,
    ): PushRule? = get(kind).firstOrNull { it.ruleId == ruleId }

    /** Our keywords: content rules that aren't the server's. */
    val keywords: List<Keyword>
        get() =
            get(PushRuleKind.Content)
                .filter { !it.default && !it.pattern.isNullOrBlank() }
                .map { Keyword(it.pattern!!, it.ruleId, it.enabled) }

    /** [roomId]'s keywords: its own, and the account's with whether each is silenced there. */
    fun roomKeywords(roomId: String): RoomKeywords {
        val here =
            get(PushRuleKind.Override).filter { it.enabled }.mapNotNull { rule ->
                roomWord(rule, roomId)?.let {
                    it to
                        rule
                }
            }
        val silenced = here.filter { (_, rule) -> rule.preset == NotifyPreset.Off }.map { it.first.lowercase() }.toSet()
        val own = here.filter { (_, rule) -> rule.preset != NotifyPreset.Off }.map { it.first }
        return RoomKeywords(own, keywords.map { it to (it.word.lowercase() in silenced) })
    }

    companion object {
        /** The ruleset in `m.push_rules`' content; empty when it has none. */
        fun of(content: JsonObject?): PushRuleset {
            val global = content?.get("global") as? JsonObject ?: return PushRuleset()
            return PushRuleset(
                PushRuleKind.entries.associateWith { kind ->
                    (global[kind.wire] as? JsonArray).orEmpty().mapNotNull {
                        (it as? JsonObject)?.let { rule ->
                            ruleOf(kind, rule)
                        }
                    }
                },
            )
        }

        private fun ruleOf(
            kind: PushRuleKind,
            json: JsonObject,
        ): PushRule? {
            val id = json.str("rule_id") ?: return null
            return PushRule(
                kind = kind,
                ruleId = id,
                default = json.bool("default") == true,
                enabled = json.bool("enabled") != false,
                actions = json["actions"] as? JsonArray ?: JsonArray(emptyList()),
                conditions = json["conditions"] as? JsonArray ?: JsonArray(emptyList()),
                pattern = json.str("pattern"),
            )
        }

        /** The word [rule] is a keyword for in [roomId]: exactly that room and a body match, nothing else. */
        internal fun roomWord(
            rule: PushRule,
            roomId: String,
        ): String? {
            if (rule.default || rule.kind != PushRuleKind.Override) return null
            val conditions = rule.conditionList()
            if (conditions.size != 2) return null
            val room = conditions.firstOrNull { it.isMatch(PushRule.ROOM_ID) }?.str("pattern")
            val word = conditions.firstOrNull { it.isMatch(PushRule.BODY) }?.str("pattern")
            return word?.takeIf { room == roomId && it.isNotBlank() }
        }
    }
}
