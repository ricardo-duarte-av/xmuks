package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeDelivery
import pt.aguiarvieira.xmuks.core.data.timeline.bridgeDeliveryOf
import pt.aguiarvieira.xmuks.core.data.timeline.bridgeOf
import pt.aguiarvieira.xmuks.core.data.timeline.deliveredThrough
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.Receipt

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

    private fun ours(
        id: String,
        ts: Long,
    ) = Event(rowId = ts, roomId = "!r", eventId = id, sender = "@me:x", type = "m.room.message", timestamp = ts)

    private fun report(
        about: String,
        ts: Long,
        delivered: Boolean,
    ) = Event(
        rowId = 100 + ts,
        roomId = "!r",
        eventId = "\$s$ts",
        sender = "@bot:x",
        type = "com.beeper.message_send_status",
        timestamp = ts,
        content = json(if (delivered) """{"status":"SUCCESS","delivered_to_users":["@u:x"]}""" else """{"status":"SUCCESS"}"""),
        relatesTo = about,
        relationType = "m.reference",
    )

    private val timeline = listOf(ours("\$a", 1), ours("\$b", 2), ours("\$c", 3), ours("\$d", 4))

    @Test
    fun aDeliveryReportConfirmsEveryEarlierMessage() {
        val refs = listOf(report("\$a", 5, false), report("\$c", 6, true), report("\$d", 7, false)).groupBy { it.relatesTo }
        assertEquals(setOf("\$a", "\$b", "\$c"), deliveredThrough(timeline, refs, emptyMap(), "@me:x"))
    }

    @Test
    fun aReadReceiptConfirmsEveryEarlierMessageButNotTheBridgeBots() {
        val refs = listOf(report("\$a", 5, false)).groupBy { it.relatesTo }
        val receipts =
            mapOf(
                "\$b" to listOf(Receipt(userId = "@u:x", receiptType = "m.read", eventId = "\$b")),
                "\$d" to listOf(Receipt(userId = "@bot:x", receiptType = "m.read", eventId = "\$d")),
            )
        assertEquals(setOf("\$a", "\$b"), deliveredThrough(timeline, refs, receipts, "@me:x"))
    }

    @Test
    fun roomsWithoutReportsGetNoTicks() {
        val receipts = mapOf("\$d" to listOf(Receipt(userId = "@u:x", receiptType = "m.read", eventId = "\$d")))
        assertEquals(emptySet<String>(), deliveredThrough(timeline, emptyMap(), receipts, "@me:x"))
    }
}
