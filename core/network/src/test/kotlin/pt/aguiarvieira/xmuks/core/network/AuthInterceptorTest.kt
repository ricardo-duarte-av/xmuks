package pt.aguiarvieira.xmuks.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private val ours = MockWebServer()
    private val elsewhere = MockWebServer()
    private val store by lazy { FakeSessionStore(credsFor(ours.url("/")), currentToken = "tok") }
    private val http by lazy { testClient(store) }

    @Before
    fun start() {
        ours.start()
        elsewhere.start()
    }

    @After
    fun stop() {
        ours.close()
        elsewhere.close()
    }

    private fun get(server: MockWebServer) = http.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

    @Test
    fun `the session cookie goes to our gomuks only`() {
        ours.enqueue(MockResponse.Builder().build())
        elsewhere.enqueue(MockResponse.Builder().build())
        get(ours)
        get(elsewhere)
        assertEquals("gomuks_auth=tok", ours.takeRequest().headers["Cookie"])
        assertNull(elsewhere.takeRequest().headers["Cookie"])
    }

    @Test
    fun `a 401 from elsewhere doesn't touch our session`() {
        elsewhere.enqueue(MockResponse.Builder().code(401).build())
        get(elsewhere)
        assertEquals("tok", store.token())
        assertEquals(1, elsewhere.requestCount)
    }
}
