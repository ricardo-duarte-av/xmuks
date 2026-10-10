package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleriesTest {
    private fun json(s: String): JsonObject = GomuksJson.parseToJsonElement(s).jsonObject

    @Test
    fun `summary counts what the gallery holds, or shows its caption`() {
        val photos = json("""{"body":"","itemtypes":[{"itemtype":"m.image"},{"itemtype":"m.image"}]}""")
        val video = json("""{"itemtypes":[{"itemtype":"m.video"}]}""")
        val mixed = json("""{"itemtypes":[{"itemtype":"m.image"},{"itemtype":"m.audio"},{"itemtype":"bogus"}]}""")
        val captioned = json("""{"body":"beach","itemtypes":[{"itemtype":"m.image"}]}""")
        assertEquals("🖼️ 2 photos", Galleries.summary(photos))
        assertEquals("🖼️ 1 video", Galleries.summary(video))
        assertEquals("🖼️ 2 items", Galleries.summary(mixed))
        assertEquals("🖼️ beach", Galleries.summary(captioned))
        assertEquals("m.image", Galleries.items(photos)[0]["msgtype"]?.toString()?.trim('"'))
    }
}
