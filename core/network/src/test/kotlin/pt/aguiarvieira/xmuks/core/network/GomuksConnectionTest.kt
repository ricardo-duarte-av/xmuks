package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import java.util.concurrent.TimeUnit

class GomuksConnectionTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private class MemoryResumeStore : ResumeStore {
        val saved = mutableListOf<ResumePoint>()
        var point = ResumePoint()

        override suspend fun load() = point

        override suspend fun save(point: ResumePoint) {
            this.point = point
            saved += point
        }
    }

    @Test
    fun `reconnects resuming from the last applied event, with the store's catch-up timestamp`() {
        server.enqueue(
            MockResponse
                .Builder()
                .body(
                    listOf(
                        """{"command":"run_id","request_id":0,"data":{"run_id":"7","listener_id":3}}""",
                        """{"command":"sync_complete","request_id":0,""" +
                            """"data":{"server_timestamp":500,"clear_state":true}}""",
                        """{"command":"init_complete","request_id":0,"data":{}}""",
                        """{"command":"typing","request_id":-10,"data":{"room_id":"!r"}}""",
                        """{"command":"sync_complete","request_id":-11,"data":{"server_timestamp":600}}""",
                    ).joinToString("\n", postfix = "\n"),
                ).build(),
        )
        server.enqueue(MockResponse.Builder().code(503).build())
        // The store owns the catch-up timestamp (only it knows the data behind it is complete).
        val store = MemoryResumeStore().apply { point = ResumePoint(lastServerTs = 450) }
        val applied = mutableListOf<GomuksFrame>()
        val session = FakeSessionStore(credsFor(server.url("/")), currentToken = "tok")
        val http = testClient(session)
        val connection =
            GomuksConnection(SseClient(http, { server.url("/") }, io), http, { server.url("/") }, store, io) {
                applied +=
                    it
            }

        runBlocking {
            val job = async { connection.run() }
            withTimeout(
                10_000
            ) { connection.state.first { it is ConnectionState.Retrying && it.error.contains("503") } }
            server.takeRequest(1, TimeUnit.SECONDS)
            val second = server.takeRequest(1, TimeUnit.SECONDS)!!.url
            job.cancel()

            assertEquals(5, applied.size)
            assertEquals("7", second.queryParameter("run_id"))
            assertEquals("-11", second.queryParameter("last_received_event"))
            assertEquals("3", second.queryParameter("prev_listener_id"))
            assertEquals("450", second.queryParameter("last_server_ts"))
            assertEquals(ResumePoint("7", -11, 3, 450), store.point)
        }
    }

    @Test
    fun `a rejected password stops the loop`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        val session = FakeSessionStore(credsFor(server.url("/")), currentToken = "expired")
        val http = testClient(session)
        val connection =
            GomuksConnection(
                SseClient(http, { server.url("/") }, io),
                http,
                { server.url("/") },
                MemoryResumeStore(),
                io
            ) {}
        runBlocking { withTimeout(10_000) { connection.run() } }
        assertEquals(ConnectionState.AuthFailed, connection.state.value)
        assertTrue(server.requestCount <= 2)
    }
}
