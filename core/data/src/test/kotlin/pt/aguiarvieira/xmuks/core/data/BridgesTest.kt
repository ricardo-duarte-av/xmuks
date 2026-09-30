package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeDelivery
import pt.aguiarvieira.xmuks.core.data.timeline.bridgeDeliveryOf
import pt.aguiarvieira.xmuks.core.data.timeline.bridgeOf
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class BridgesTest {
    private fun json(s: String) = GomuksJson.parseToJsonElement(s) as JsonObject

    private fun event(
        type: String,
        content: String,
        ts: Long = 0,
        stateKey: String? = null,
    ) = Event(rowId = ts + 1, roomId = "!r", eventId = "\$e$ts", sender = "@bot:x", type = type, timestamp = ts, content = json(content), stateKey = stateKey)

    @Test
    fun readsTheBridge() {
        val bridge =
            bridgeOf(
                listOf(
                    event(
                        "m.bridge",
                        """{"bridgebot":"@whatsappbot:x","channel":{"displayname":"Carla"},
                        "protocol":{"displayname":"WhatsApp","avatar_url":"mxc://m/wa","id":"whatsapp"}}""",
                        stateKey = "x/whatsapp",
                    ),
                ),
            )!!
        assertEquals("WhatsApp", bridge.protocol)
        assertEquals("mxc://m/wa", bridge.protocolAvatarMxc)
        assertEquals("Carla", bridge.channel)
        assertEquals("@whatsappbot:x", bridge.bot)
    }

    @Test
    fun deliveryFollowsTheReports() {
        val sent = event("com.beeper.message_send_status", """{"status":"SUCCESS"}""", 1)
        val delivered = event("com.beeper.message_send_status", """{"status":"SUCCESS","delivered_to_users":["@u:x"]}""", 2)
        val failed = event("com.beeper.message_send_status", """{"status":"FAIL_RETRIABLE"}""", 3)
        assertNull(bridgeDeliveryOf(emptyList()))
        assertEquals(BridgeDelivery.Sent, bridgeDeliveryOf(listOf(sent)))
        assertEquals(BridgeDelivery.Delivered, bridgeDeliveryOf(listOf(delivered)))
        assertEquals(BridgeDelivery.Delivered, bridgeDeliveryOf(listOf(sent, delivered)))
        assertEquals(BridgeDelivery.Failed, bridgeDeliveryOf(listOf(sent, failed)))
    }
}
