package pt.aguiarvieira.xmuks.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity bound on decode cost for a real-sized initial sync (webmuks: 649 rooms, 1.9 MB, largest line
 * 0.33 MB). Generous on purpose — it guards against pathological regressions, not CI jitter.
 */
class DecodePerformanceTest {
    @Test
    fun `decodes a 650-room initial sync quickly`() {
        val lines = (0 until 7).map { chunk -> syncLine(chunk * 100 until minOf(650, (chunk + 1) * 100)) }
        val bytes = lines.sumOf { it.length }
        repeat(3) { lines.forEach(FrameDecoder::decode) } // JIT warm-up
        val start = System.nanoTime()
        val rooms = lines.sumOf { (FrameDecoder.decode(it)!!.event as GomuksEvent.Sync).sync.rooms.size }
        val millis = (System.nanoTime() - start) / 1_000_000
        println("decoded $rooms rooms, ${bytes / 1024} KiB in $millis ms")
        assertEquals(650, rooms)
        assertTrue("took $millis ms", millis < 2_000)
    }

    private fun syncLine(range: IntRange): String =
        buildString {
            append("""{"command":"sync_complete","request_id":0,"data":{"server_timestamp":1,"rooms":{""")
            range.forEachIndexed { i, n ->
                if (i > 0) append(',')
                append(
                    """"!r$n:example.org":{"meta":{"room_id":"!r$n:example.org","name":"Room $n","name_quality":3,""" +
                        """"lazy_load_summary":{"m.heroes":["@u$n:example.org"],"m.joined_member_count":$n},""" +
                        """"preview_event_rowid":$n,"sorting_timestamp":$n,"unread_messages":${n % 7}},""" +
                        """"account_data":{"m.fully_read":{"type":"m.fully_read",""" +
                        """"content":{"event_id":"${'$'}e$n"}}},""" +
                        """"events":[""",
                )
                repeat(3) { e ->
                    if (e > 0) append(',')
                    append(
                        """{"rowid":${n * 10 + e},"room_id":"!r$n:example.org","event_id":"${'$'}e$n-$e",""" +
                            """"sender":"@u$n:example.org","type":"m.room.message","timestamp":$n,""" +
                            """"content":{"msgtype":"m.text","body":"${"lorem ipsum ".repeat(20)}"},""" +
                            """"local_content":{"sanitized_html":"<b>x</b>","preview_text":"preview $n"}}""",
                    )
                }
                append("]}")
            }
            append("}}}")
        }
}
