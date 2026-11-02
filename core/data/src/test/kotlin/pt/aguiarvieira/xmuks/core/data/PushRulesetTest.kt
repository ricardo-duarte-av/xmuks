package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.push.Keyword
import pt.aguiarvieira.xmuks.core.data.push.NotifyPreset
import pt.aguiarvieira.xmuks.core.data.push.PushRule
import pt.aguiarvieira.xmuks.core.data.push.PushRuleKind
import pt.aguiarvieira.xmuks.core.data.push.PushRuleset
import pt.aguiarvieira.xmuks.core.data.push.with
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class PushRulesetTest {
    private fun actions(json: String): JsonArray = GomuksJson.parseToJsonElement(json).jsonArray

    private fun ruleset(global: String) = PushRuleset.of(GomuksJson.parseToJsonElement("""{"global": $global}""").jsonObject)

    @Test
    fun `actions read as presets, anything else as custom`() {
        assertEquals(NotifyPreset.Off, NotifyPreset.of(actions("[]")))
        assertEquals(NotifyPreset.Off, NotifyPreset.of(actions("""["dont_notify"]""")))
        assertEquals(NotifyPreset.Notify, NotifyPreset.of(actions("""["notify"]""")))
        assertEquals(NotifyPreset.Notify, NotifyPreset.of(actions("""["notify", {"set_tweak": "highlight", "value": false}]""")))
        assertEquals(NotifyPreset.NotifySound, NotifyPreset.of(actions("""["notify", {"set_tweak": "sound", "value": "default"}]""")))
        assertEquals(
            NotifyPreset.Highlight,
            NotifyPreset.of(actions("""["notify", {"set_tweak": "highlight"}, {"set_tweak": "sound", "value": "ring"}]""")),
        )
        assertEquals(NotifyPreset.Custom, NotifyPreset.of(actions("""["notify", {"set_tweak": "highlight"}]""")))
        assertEquals(NotifyPreset.Custom, NotifyPreset.of(actions("""["notify", {"set_tweak": "org.example.flash"}]""")))
        assertEquals(NotifyPreset.Custom, NotifyPreset.of(actions("""[{"set_tweak": "sound", "value": "default"}]""")))
    }

    @Test
    fun `each choice's actions read back as it`() {
        NotifyPreset.choices.forEach { assertEquals(it, NotifyPreset.of(it.actions())) }
    }

    @Test
    fun `rules parse by kind, with whom and what room they're about`() {
        val rules =
            ruleset(
                """{
                "override": [{"rule_id": ".m.rule.master", "default": true, "enabled": false, "actions": []},
                             {"rule_id": "!a:x", "actions": [], "conditions": [{"kind": "event_match", "key": "room_id", "pattern": "!a:x"}]}],
                "content": [{"rule_id": "deploy", "pattern": "deploy", "actions": ["notify"]}],
                "room": [{"rule_id": "!b:x", "actions": ["notify"]}],
                "sender": [{"rule_id": "@bot:x", "actions": []}],
                "underride": [{"actions": ["notify"]}]
            }""",
            )
        val master = rules[PushRuleKind.Override].first()
        assertEquals(true, master.default)
        assertEquals(false, master.enabled)
        assertEquals("!a:x", rules[PushRuleKind.Override][1].roomId)
        assertEquals("!b:x", rules[PushRuleKind.Room].single().roomId)
        assertEquals("@bot:x", rules[PushRuleKind.Sender].single().userId)
        assertNull(rules[PushRuleKind.Content].single().roomId)
        // No rule_id: not a rule we could ever change.
        assertEquals(emptyList<PushRule>(), rules[PushRuleKind.Underride])
        assertEquals(listOf(Keyword("deploy", "deploy", true)), rules.keywords)
    }

    @Test
    fun `a room's keywords are its own, and the account's silenced or not there`() {
        fun override(
            id: String,
            room: String,
            word: String,
            actions: String,
        ) = """{"rule_id": "$id", "actions": $actions, "conditions": [
                {"kind": "event_match", "key": "room_id", "pattern": "$room"},
                {"kind": "event_match", "key": "content.body", "pattern": "$word"}]}"""
        val rules =
            ruleset(
                """{
                "override": [${override("a", "!r", "lunch", """["notify"]""")}, ${override("b", "!r", "Deploy", "[]")},
                             ${override("c", "!other", "coffee", """["notify"]""")}],
                "content": [{"rule_id": ".m.rule.contains_user_name", "default": true, "pattern": "me", "actions": ["notify"]},
                            {"rule_id": "deploy", "pattern": "deploy", "actions": ["notify"]},
                            {"rule_id": "release", "pattern": "release", "actions": ["notify"], "enabled": false}]
            }""",
            )
        val here = rules.roomKeywords("!r")
        assertEquals(listOf("lunch"), here.own)
        assertEquals(
            listOf(Keyword("deploy", "deploy", true) to true, Keyword("release", "release", false) to false),
            here.global,
        )
    }

    @Test
    fun `edits change rules in place, add new ones first, take deleted ones away`() {
        val rules = ruleset("""{"content": [{"rule_id": "a", "pattern": "a", "actions": []}, {"rule_id": "b", "pattern": "b", "actions": []}]}""")
        val edited =
            rules.with(
                mapOf(
                    (PushRuleKind.Content to "new") to PushRule(PushRuleKind.Content, "new", pattern = "new"),
                    (PushRuleKind.Content to "a") to null,
                    (PushRuleKind.Content to "b") to PushRule(PushRuleKind.Content, "b", enabled = false, pattern = "b"),
                ),
            )
        assertEquals(listOf("new", "b"), edited[PushRuleKind.Content].map { it.ruleId })
        assertEquals(false, edited.find(PushRuleKind.Content, "b")?.enabled)
    }
}
