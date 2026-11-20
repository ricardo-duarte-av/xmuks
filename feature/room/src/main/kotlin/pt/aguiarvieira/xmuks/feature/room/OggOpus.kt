package pt.aguiarvieira.xmuks.feature.room

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Re-wraps an Ogg Opus recording's packets as RFC 7845 asks, the audio untouched. Android's
 * MediaRecorder stamps each page with where its packet *starts* (a frame early: the stream seems
 * to begin before zero), puts one 20 ms packet per page, and never marks the last page. Players
 * mostly don't mind; WhatsApp does ("audio file not available", seen through the bridge): it needs
 * each page's granule position to be where its last packet *ends*. Fixed here: those positions,
 * [PACKETS_PER_PAGE] packets a page (a quarter less overhead), and end-of-stream on the last.
 */
@Suppress("MagicNumber") // the Ogg (RFC 3533) and Opus (RFC 6716) layouts, as they are
internal object OggOpus {
    private const val PACKETS_PER_PAGE = 50
    private const val MAX_LACING = 255
    private const val HEADER_BYTES = 27
    private const val FLAG_FIRST = 2
    private const val FLAG_LAST = 4
    private val CAPTURE = "OggS".toByteArray()
    private val CRC = crcTable()

    /** [ogg] repacked; null when it isn't an Ogg Opus stream this understands (send it as it was). */
    fun repack(ogg: ByteArray): ByteArray? {
        val (serial, packets) = readPackets(ogg) ?: return null
        if (packets.size < 2 || !packets[0].startsWith("OpusHead") || !packets[1].startsWith("OpusTags")) return null
        val audio = packets.drop(2).filter { it.isNotEmpty() }
        val out = ByteArrayOutputStream(ogg.size)
        var sequence = 0
        out.write(page(FLAG_FIRST, 0, serial, sequence++, listOf(packets[0])) ?: return null)
        out.write(page(0, 0, serial, sequence++, listOf(packets[1])) ?: return null)
        var position = 0L
        val pages = audio.chunked(PACKETS_PER_PAGE)
        pages.forEachIndexed { index, chunk ->
            for (packet in chunk) position += samples(packet) ?: return null
            val flags = if (index == pages.lastIndex) FLAG_LAST else 0
            out.write(page(flags, position, serial, sequence++, chunk) ?: return null)
        }
        return out.toByteArray()
    }

    /** The stream's serial number and its packets, put back together across pages. */
    @Suppress("ReturnCount") // each way a file can be malformed
    private fun readPackets(ogg: ByteArray): Pair<Int, List<ByteArray>>? {
        val packets = ArrayList<ByteArray>()
        val current = ByteArrayOutputStream()
        var serial: Int? = null
        var at = 0
        while (at < ogg.size) {
            if (at + HEADER_BYTES > ogg.size || !ogg.copyOfRange(at, at + 4).contentEquals(CAPTURE)) return null
            val header = ByteBuffer.wrap(ogg, at, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            val pageSerial = header.getInt(at + 14)
            // One logical stream only (a chained or multiplexed file isn't a recording of ours).
            if (serial != null && pageSerial != serial) return null
            serial = pageSerial
            val segments = ogg[at + 26].toInt() and 0xff
            var body = at + HEADER_BYTES + segments
            for (i in 0 until segments) {
                val length = ogg.getOrNull(at + HEADER_BYTES + i)?.toInt()?.and(0xff) ?: return null
                if (body + length > ogg.size) return null
                current.write(ogg, body, length)
                body += length
                if (length < MAX_LACING) {
                    packets += current.toByteArray()
                    current.reset()
                }
            }
            at = body
        }
        return (serial ?: return null) to packets
    }

    /** How many 48 kHz samples an Opus packet holds, from its TOC byte (RFC 6716 §3.1). */
    internal fun samples(packet: ByteArray): Int? {
        val toc = packet[0].toInt() and 0xff
        val config = toc shr 3
        val frame =
            when {
                config < 12 -> intArrayOf(480, 960, 1920, 2880)[config % 4]
                config < 16 -> intArrayOf(480, 960)[config % 2]
                else -> intArrayOf(120, 240, 480, 960)[config % 4]
            }
        val frames =
            when (toc and 3) {
                0 -> 1
                1, 2 -> 2
                else -> packet.getOrNull(1)?.toInt()?.and(0x3f) ?: return null
            }
        return frame * frames
    }

    private fun page(
        flags: Int,
        granule: Long,
        serial: Int,
        sequence: Int,
        packets: List<ByteArray>,
    ): ByteArray? {
        val lacing = ByteArrayOutputStream()
        for (packet in packets) {
            repeat(packet.size / MAX_LACING) { lacing.write(MAX_LACING) }
            lacing.write(packet.size % MAX_LACING)
        }
        if (lacing.size() > MAX_LACING) return null
        val bodySize = packets.sumOf { it.size }
        val buffer = ByteBuffer.allocate(HEADER_BYTES + lacing.size() + bodySize).order(ByteOrder.LITTLE_ENDIAN)
        buffer
            .put(CAPTURE)
            .put(0.toByte())
            .put(flags.toByte())
            .putLong(granule)
            .putInt(serial)
            .putInt(sequence)
        buffer.putInt(0) // the checksum, filled in below
        buffer.put(lacing.size().toByte()).put(lacing.toByteArray())
        packets.forEach { buffer.put(it) }
        val bytes = buffer.array()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(22, crc(bytes))
        return bytes
    }

    /** Ogg's CRC-32: polynomial 0x04c11db7, not reflected, starting from 0. */
    internal fun crc(bytes: ByteArray): Int {
        var crc = 0
        for (b in bytes) crc = (crc shl 8) xor CRC[((crc ushr 24) xor (b.toInt() and 0xff)) and 0xff]
        return crc
    }

    private fun crcTable() =
        IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if ((r and Int.MIN_VALUE) != 0) (r shl 1) xor 0x04c11db7 else r shl 1 }
            r
        }

    private fun ByteArray.startsWith(magic: String) =
        size >= magic.length && copyOfRange(0, magic.length).contentEquals(magic.toByteArray())
}
