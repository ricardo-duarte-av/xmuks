package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExecClientTest {
    private val server = MockWebServer()
    private val store by lazy { FakeSessionStore(credsFor(server.url("/")), currentToken = "tok") }
    private val exec by lazy {
        ExecClient(testClient(store), { server.url("/") }, io, clock = { 1_000L }, retryDelaysMs = listOf(1, 1, 1))
    }

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    @Test
    fun `writes retry under the same transaction id`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().body("""{"event_id":"${'$'}x"}""").build())
        val result =
            runBlocking {
                exec.exec(
                    "send_message",
                    buildJsonObject { put("room_id", JsonPrimitive("!r")) },
                    ExecMode.Write
                )
            }

        assertEquals("\$x", ((result as ExecResult.Ok).data.jsonObject["event_id"] as JsonPrimitive).content)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/_gomuks/exec/send_message", first.url.encodedPath)
        assertTrue(first.url.queryParameter("txn_id")!!.startsWith("xmuks-"))
        assertEquals(first.url.queryParameter("txn_id"), second.url.queryParameter("txn_id"))
        assertEquals("1000", second.url.queryParameter("start_ts"))
        assertEquals("""{"room_id":"!r"}""", second.body!!.utf8())
    }

    @Test
    fun `reads carry no transaction envelope`() {
        server.enqueue(MockResponse.Builder().body("{}").build())
        runBlocking { exec.exec("get_room_state", mode = ExecMode.Read) }
        assertNull(server.takeRequest().url.queryParameter("txn_id"))
    }

    @Test
    fun `command errors are decoded, not retried`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(418)
                .body("""{"errcode":"M_FORBIDDEN","error":"nope"}""")
                .build()
        )
        val result = runBlocking { exec.exec("redact_event", mode = ExecMode.Write) }
        assertEquals(ExecResult.CommandError(418, "M_FORBIDDEN", "nope"), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired token is refreshed once and the request replayed`() {
        store.creds = store.creds!!.copy() // plain-HTTP creds: refresh goes through a stubbed login below
        val refreshing =
            ExecClient(
                testClientWithLogin(store) { "fresh" },
                { server.url("/") },
                io,
                retryDelaysMs = emptyList(),
            )
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .body("""{"errcode":"FI.MAU.GOMUKS.INVALID_COOKIE"}""")
                .build()
        )
        server.enqueue(MockResponse.Builder().body("{}").build())
        val result = runBlocking { refreshing.exec("get_event", mode = ExecMode.Read) }
        assertTrue(result is ExecResult.Ok)
        assertEquals("gomuks_auth=tok", server.takeRequest().headers["Cookie"])
        assertEquals("gomuks_auth=fresh", server.takeRequest().headers["Cookie"])
        assertEquals("fresh", store.currentToken)
    }

    private fun testClientWithLogin(
        store: SessionStore,
        token: () -> String,
    ) = okhttp3.OkHttpClient
        .Builder()
        .addInterceptor(CompressionInterceptor())
        .addInterceptor(
            AuthInterceptor(
                store,
                object : AuthApi(okhttp3.OkHttpClient()) {
                    override fun login(credentials: Credentials) = AuthResult.Success(token())
                }
            ),
        ).build()
}
