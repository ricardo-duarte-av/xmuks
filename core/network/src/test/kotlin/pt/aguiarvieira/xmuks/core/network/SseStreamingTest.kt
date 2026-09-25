package pt.aguiarvieira.xmuks.core.network

import com.github.luben.zstd.ZstdOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SseStreamingTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    /**
     * gomuks' `/sse` is one zstd frame that never ends, flushed after each event. The decoder must
     * hand over each event while the frame is still open — this test holds the server mid-frame until
     * the client has seen the first event, so a buffering decoder deadlocks into a timeout.
     */
    @Test
    fun `zstd events are delivered before the frame ends`() {
        val clientSawFirst = CountDownLatch(1)
        server.enqueue(
            MockResponse
                .Builder()
                .setHeader("Content-Type", "application/jsonl")
                .setHeader("Content-Encoding", "zstd")
                .body(
                    object : MockResponseBody {
                        override val contentLength = -1L

                        override fun writeTo(sink: BufferedSink) {
                            val zstd = ZstdOutputStream(sink.outputStream())
                            zstd.write(RUN_ID.toByteArray())
                            zstd.flush()
                            sink.flush()
                            check(clientSawFirst.await(10, TimeUnit.SECONDS)) { "client never decoded the first event" }
                            zstd.write("null\n$INIT\n".toByteArray())
                            zstd.close()
                        }
                    },
                ).build(),
        )
        val store = FakeSessionStore(credsFor(server.url("/")), currentToken = "tok")
        val sse = SseClient(testClient(store), { server.url("/") }, io)

        val frames =
            runBlocking {
                withTimeout(15_000) {
                    sse
                        .open(ResumePoint())
                        .take(2)
                        .toList { if (it.event is GomuksEvent.RunId) clientSawFirst.countDown() }
                }
            }
        assertEquals(listOf("run_id", "init_complete"), frames.map { it.command })

        val request = server.takeRequest()
        assertEquals("application/jsonl", request.headers["Accept"])
        assertTrue(request.headers["Accept-Encoding"]!!.contains("zstd"))
        assertEquals("gomuks_auth=tok", request.headers["Cookie"])
    }

    /** Stopping the stream (app backgrounded) must not wait for the next ping or a read timeout. */
    @Test
    fun `cancelling an idle stream tears it down immediately`() {
        val release = CountDownLatch(1)
        server.enqueue(
            MockResponse
                .Builder()
                .body(
                    object : MockResponseBody {
                        override val contentLength = -1L

                        override fun writeTo(sink: BufferedSink) {
                            sink.writeUtf8(RUN_ID)
                            sink.flush()
                            release.await(20, TimeUnit.SECONDS) // an idle stream between pings
                        }
                    },
                ).build(),
        )
        val store = FakeSessionStore(credsFor(server.url("/")), currentToken = "tok")
        val sse = SseClient(testClient(store, readTimeoutS = 30), { server.url("/") }, io)
        val started = System.nanoTime()
        try {
            runBlocking { withTimeout(5_000) { sse.open(ResumePoint()).first() } }
        } finally {
            release.countDown()
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("teardown took $ms ms", ms < 3_000)
    }

    @Test
    fun `resume point becomes query parameters`() {
        server.enqueue(MockResponse.Builder().body("$RUN_ID\n").build())
        val store = FakeSessionStore(credsFor(server.url("/")), currentToken = "tok")
        val sse = SseClient(testClient(store), { server.url("/") }, io)
        runBlocking { sse.open(ResumePoint("123", -51012, 42, 1790374225978)).first() }
        val url = server.takeRequest().url
        assertEquals("/_gomuks/sse", url.encodedPath)
        assertEquals("123", url.queryParameter("run_id"))
        assertEquals("-51012", url.queryParameter("last_received_event"))
        assertEquals("42", url.queryParameter("prev_listener_id"))
        assertEquals("1790374225978", url.queryParameter("last_server_ts"))
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.toList(onEach: (T) -> Unit): List<T> {
        val out = mutableListOf<T>()
        collect {
            out += it
            onEach(it)
        }
        return out
    }

    companion object {
        const val RUN_ID = """{"command":"run_id","request_id":0,"data":{"run_id":"123","listener_id":42}}""" + "\n"
        const val INIT = """{"command":"init_complete","request_id":0,"data":{}}"""
    }
}
