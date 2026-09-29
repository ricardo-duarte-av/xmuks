package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class RoomPushRulesTest {
    private fun rules(
        override: String = "",
        room: String = "",
    ): JsonObject = GomuksJson.parseToJsonElement("""{"global": {"override": [$override], "room": [$room]}}""").jsonObject

    @Test
    fun `each setting read from the rules`() {
        assertEquals(RoomNotifications.Default, RoomPushRules.settingOf(rules(), "!r"))
        assertEquals(
            RoomNotifications.All,
            RoomPushRules.settingOf(rules(room = """{"rule_id": "!r", "enabled": true, "actions": ["notify"]}"""), "!r"),
        )
        assertEquals(
            RoomNotifications.MentionsAndKeywords,
            RoomPushRules.settingOf(rules(room = """{"rule_id": "!r", "enabled": true, "actions": []}"""), "!r"),
        )
        assertEquals(
            RoomNotifications.Off,
            RoomPushRules.settingOf(
                rules(
                    override = """{"rule_id": "!r", "enabled": true, "actions": []}""",
                    room = """{"rule_id": "!r", "actions": ["notify"]}""",
                ),
                "!r",
            ),
        )
    }

    @Test
    fun `disabled rules and other rooms don't count`() {
        assertEquals(RoomNotifications.Default, RoomPushRules.settingOf(rules(override = """{"rule_id": "!r", "enabled": false, "actions": []}"""), "!r"))
        assertEquals(RoomNotifications.Default, RoomPushRules.settingOf(rules(room = """{"rule_id": "!other", "actions": []}"""), "!r"))
        assertEquals(RoomNotifications.Default, RoomPushRules.settingOf(null, "!r"))
    }
}
