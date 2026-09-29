package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.media.BlurhashEncoder
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.PreparedMedia
import pt.aguiarvieira.xmuks.core.data.media.Thumbnail
import pt.aguiarvieira.xmuks.core.data.media.UploadSource
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.mediaMessage
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.File

class MediaSendingTest {
    private fun json(text: String): JsonObject = GomuksJson.parseToJsonElement(text).jsonObject

    // Expected hashes come from github.com/buckket/go-blurhash (what gomuks uses) on the same pixels.

    @Test
    fun `blurhash matches the reference - flat colour`() {
        val white = IntArray(16) { 0xFFFFFFFF.toInt() }
        assertEquals("L~TSUA~qfQ~q~q%MfQ%MfQfQfQfQ", BlurhashEncoder.encode(white, 4, 4))
    }

    @Test
    fun `blurhash matches the reference - gradient, 5x2 components`() {
        val pixels = IntArray(8 * 6) { i -> (0xFF shl 24) or (i * 5 shl 16) or (255 - i * 5 shl 8) or (i * 3) }
        assertEquals("D|G9x}k-X5tPX5HJXhj@XOj@", BlurhashEncoder.encode(pixels, 8, 6, componentsX = 5, componentsY = 2))
    }

    private fun prepared(
        width: Int? = null,
        height: Int? = null,
    ) = PreparedMedia(
        UploadSource(0) { ByteArray(0).inputStream() },
        "clip.mp4",
        "video/mp4",
        MediaKind.Video,
        width,
        height,
        Thumbnail(ByteArray(1234), 800, 450, "LEHV6nWB2yk8", File("thumb.jpg")),
    )

    @Test
    fun `our thumbnail replaces gomuks' first frame, rotation-corrected size kept`() {
        val gomuks =
            json(
                """
                {"msgtype": "m.video", "body": "clip.mp4", "url": "mxc://s/video",
                 "info": {"mimetype": "video/mp4", "w": 1920, "h": 1080, "duration": 5000,
                          "thumbnail_url": "mxc://s/first", "thumbnail_info": {"w": 1920}}}
                """,
            )
        val out = MediaSender.withOurs(gomuks, prepared(1080, 1920), json("""{"url": "mxc://s/quarter"}"""))
        val info = out["info"]!!.jsonObject
        assertEquals("mxc://s/quarter", info["thumbnail_url"]!!.jsonPrimitive.content)
        assertEquals("1080", info["w"].toString())
        assertEquals("1920", info["h"].toString())
        assertEquals("5000", info["duration"].toString())
        val thumbInfo = info["thumbnail_info"]!!.jsonObject
        assertEquals("800", thumbInfo["w"].toString())
        assertEquals("1234", thumbInfo["size"].toString())
        assertEquals("LEHV6nWB2yk8", thumbInfo["xyz.amorgan.blurhash"]!!.jsonPrimitive.content)
        assertEquals("mxc://s/video", out["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `encrypted thumbnails go in thumbnail_file, never beside a thumbnail_url`() {
        val gomuks = json("""{"msgtype": "m.video", "file": {"url": "mxc://s/v"}, "info": {"thumbnail_file": {"url": "mxc://s/old"}}}""")
        val out = MediaSender.withOurs(gomuks, prepared(), json("""{"file": {"url": "mxc://s/new", "key": {}}}"""))
        val info = out["info"]!!.jsonObject
        assertFalse("thumbnail_url" in info)
        assertTrue(info["thumbnail_file"].toString().contains("mxc://s/new"))
    }

    @Test
    fun `voice messages bring their waveform, scaled to the loudest sample, and duration`() {
        val content =
            json(
                """
                {"msgtype": "m.audio", "body": "voice.ogg", "filename": "voice.ogg", "url": "mxc://s/a",
                 "info": {"mimetype": "audio/ogg", "duration": 3120},
                 "org.matrix.msc1767.audio": {"duration": 3120, "waveform": [0, 64, 256, 128]},
                 "org.matrix.msc3245.voice": {}}
                """,
            )
        val audio = mediaMessage("m.audio", content, "voice.ogg") as MessageContent.Audio
        assertEquals(listOf(0f, 0.25f, 1f, 0.5f), audio.waveform)
        assertEquals(3120L, audio.durationMs)
        assertTrue(audio.voice)
        val plain = mediaMessage("m.audio", json("""{"msgtype": "m.audio", "body": "song.mp3", "url": "mxc://s/b"}"""), "song.mp3")
        assertEquals(null, (plain as MessageContent.Audio).waveform)
        assertFalse(plain.voice)
    }
}
