package pt.aguiarvieira.xmuks.feature.room

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OggOpusTest {
    private data class Page(
        val flags: Int,
        val granule: Long,
        val sequence: Int,
        val packets: List<ByteArray>,
        val crcOk: Boolean,
    )

    /** 20 ms CELT fullband, one frame: 960 samples. */
    private val toc20ms = (31 shl 3).toByte()

    private val head = "OpusHead".toByteArray() + byteArrayOf(1, 1, 0x38, 1, 0x80.toByte(), 0xbb.toByte(), 0, 0, 0, 0, 0)
    private val tags = "OpusTags".toByteArray() + byteArrayOf(7, 0, 0, 0) + "libopus".toByteArray() + byteArrayOf(0, 0, 0, 0)

    private fun audio(count: Int) =
        List(count) { i ->
            // One packet longer than a lacing value, so it spans two of them.
            val size = if (i == 7) 300 else 3 + i % 80
            ByteArray(size) { if (it == 0) toc20ms else (i + it).toByte() }
        }

    /** What MediaRecorder writes: one packet a page, stamped where it starts, no end-of-stream. */
    private fun recorderStyle(audio: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(page(2, 0, 0, listOf(head)))
        out.write(page(0, 0, 1, listOf(tags)))
        audio.forEachIndexed { i, packet -> out.write(page(0, i * 960L, i + 2, listOf(packet))) }
        return out.toByteArray()
    }

    private fun page(
        flags: Int,
        granule: Long,
        sequence: Int,
        packets: List<ByteArray>,
    ): ByteArray {
        val lacing = ByteArrayOutputStream()
        packets.forEach { p ->
            repeat(p.size / 255) { lacing.write(255) }
            lacing.write(p.size % 255)
        }
        val b = ByteBuffer.allocate(27 + lacing.size() + packets.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
        b.put("OggS".toByteArray()).put(0.toByte()).put(flags.toByte()).putLong(granule).putInt(SERIAL).putInt(sequence).putInt(0)
        b.put(lacing.size().toByte()).put(lacing.toByteArray())
        packets.forEach { b.put(it) }
        val bytes = b.array()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(22, OggOpus.crc(bytes))
        return bytes
    }

    private fun pages(ogg: ByteArray): List<Page> {
        val pages = ArrayList<Page>()
        var at = 0
        while (at < ogg.size) {
            val h = ByteBuffer.wrap(ogg).order(ByteOrder.LITTLE_ENDIAN)
            val segments = ogg[at + 26].toInt() and 0xff
            var body = at + 27 + segments
            val packets = ArrayList<ByteArray>()
            val current = ByteArrayOutputStream()
            for (i in 0 until segments) {
                val len = ogg[at + 27 + i].toInt() and 0xff
                current.write(ogg, body, len)
                body += len
                if (len < 255) {
                    packets += current.toByteArray()
                    current.reset()
                }
            }
            val raw = ogg.copyOfRange(at, body)
            val stored = h.getInt(at + 22)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(22, 0)
            pages += Page(ogg[at + 5].toInt(), h.getLong(at + 6), h.getInt(at + 18), packets, OggOpus.crc(raw) == stored)
            assertEquals(SERIAL, h.getInt(at + 14))
            at = body
        }
        return pages
    }

    @Test
    fun `stamps each page where its last packet ends, 50 packets a page, last page marked`() {
        val audio = audio(120)
        val out = pages(OggOpus.repack(recorderStyle(audio))!!)

        assertEquals(5, out.size)
        assertEquals(listOf(0, 1, 2, 3, 4), out.map { it.sequence })
        assertEquals(listOf(2, 0, 0, 0, 4), out.map { it.flags })
        // 50, 100 and 120 packets of 960 samples.
        assertEquals(listOf(0L, 0L, 48_000L, 96_000L, 115_200L), out.map { it.granule })
        assertEquals(listOf(1, 1, 50, 50, 20), out.map { it.packets.size })
        assertTrue(out.all { it.crcOk })
        // The audio itself is untouched, in order.
        val packets = out.flatMap { it.packets }
        assertArrayEquals(head, packets[0])
        assertArrayEquals(tags, packets[1])
        audio.forEachIndexed { i, p -> assertArrayEquals(p, packets[i + 2]) }
    }

    @Test
    fun `a short recording is one marked page`() {
        val out = pages(OggOpus.repack(recorderStyle(audio(3)))!!)
        assertEquals(3, out.size)
        assertEquals(4, out.last().flags)
        assertEquals(2_880L, out.last().granule)
    }

    @Test
    fun `packet durations follow the TOC byte`() {
        // SILK 60 ms; CELT 2.5 ms x2 (code 1); hybrid 10 ms, code 3 with 4 frames.
        assertEquals(2_880, OggOpus.samples(byteArrayOf((3 shl 3).toByte())))
        assertEquals(240, OggOpus.samples(byteArrayOf(((16 shl 3) or 1).toByte())))
        assertEquals(1_920, OggOpus.samples(byteArrayOf(((12 shl 3) or 3).toByte(), 4)))
    }

    @Test
    fun `anything else is left alone`() {
        assertNull(OggOpus.repack("not an ogg file at all, just bytes".toByteArray()))
        assertNull(OggOpus.repack(ByteArray(0)))
        // Ogg, but not Opus.
        assertNull(OggOpus.repack(page(2, 0, 0, listOf("\u0001vorbis".toByteArray()))))
    }

    private companion object {
        const val SERIAL = 0x1234567
    }
}
