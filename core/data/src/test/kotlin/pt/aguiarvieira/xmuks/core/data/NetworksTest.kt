package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.SenderNetwork
import pt.aguiarvieira.xmuks.core.data.timeline.networksOf
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class NetworksTest {
    @Test
    fun `m bridge state gives each network's name and logo by protocol ID`() {
        val content =
            """
            {"bridgebot":"@whatsappbot:x","channel":{"displayname":"Thalita","id":"1@lid"},
             "protocol":{"avatar_url":"mxc://maunium.net/wa","displayname":"WhatsApp","id":"whatsapp"}}
            """.trimIndent()
        val bridge =
            Event(
                rowId = 1,
                timelineRowId = -1,
                roomId = "!r",
                eventId = "\$b",
                sender = "@whatsappbot:x",
                type = "m.bridge",
                stateKey = "x/whatsapp",
                timestamp = 0,
                content = GomuksJson.parseToJsonElement(content).jsonObject,
            )
        assertEquals(mapOf("whatsapp" to SenderNetwork("WhatsApp", "mxc://maunium.net/wa")), networksOf(listOf(bridge)))
    }
}
