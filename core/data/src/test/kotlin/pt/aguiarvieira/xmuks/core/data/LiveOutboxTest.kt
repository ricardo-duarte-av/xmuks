package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.network.SessionStore
import pt.aguiarvieira.xmuks.core.network.parseServerUrl
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse
import java.util.concurrent.TimeUnit

/**
 * Opt-in, and it WRITES: sends real messages into [XMUKS_LIVE_SEND_ROOM] — only ever a room you
 * may write to. Proves the outbox's contract against a real gomuks: accepted → local echo; a retry
 * under the same envelope is collapsed (same event, not a second message); the message then goes out.
 *
 *     XMUKS_LIVE_SERVER=https://… XMUKS_LIVE_USER=… XMUKS_LIVE_PASS=… XMUKS_LIVE_SEND_ROOM='!room:server' \
 *         ./gradlew :core:data:testDebugUnitTest --tests '*LiveOutboxTest*' -i
 */
@RunWith(AndroidJUnit4::class)
class LiveOutboxTest {
    private val server = System.getenv("XMUKS_LIVE_SERVER")?.let(::parseServerUrl)
    private val user = System.getenv("XMUKS_LIVE_USER")
    private val pass = System.getenv("XMUKS_LIVE_PASS")
    private val room = System.getenv("XMUKS_LIVE_SEND_ROOM")

    private fun exec(): ExecClient {
        val session =
            object : SessionStore {
                var token: String? = null

                override fun credentials() = Credentials(server!!, user!!, pass!!)

                override fun token() = token

                override fun saveToken(token: String?) {
                    this.token = token
                }
            }
        val plain = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()
        val http = plain.newBuilder().addInterceptor(AuthInterceptor(session, AuthApi(plain))).build()
        return ExecClient(http, { server }, Dispatchers.IO)
    }

    private fun params(text: String) =
        buildJsonObject {
            put("room_id", JsonPrimitive(room))
            put("text", JsonPrimitive(text))
        }

    @Test
    fun `send through the outbox, retries collapse, and it goes out`() =
        runBlocking {
            assumeTrue(server != null && user != null && pass != null && room != null)
            val exec = exec()
            val db =
                OutboxDatabase.build(ApplicationProvider.getApplicationContext(), name = null, driver = AndroidSQLiteDriver())
            val echoes = mutableListOf<Event>()
            val outbox = Outbox(db.outboxDao(), exec::execOnce, { echoes += it }, CoroutineScope(Dispatchers.IO))

            // 1. Through the outbox: accepted, gone from the outbox, its local echo handed on.
            outbox.sendMessage(room!!, params("xmuks outbox live test — **markdown** ${System.currentTimeMillis()}"))
            outbox.sendPass()
            val echo = echoes.single()
            println("local echo: rowid=${echo.rowId} pending=${echo.pending} txn=${echo.transactionId}")
            assertTrue(echo.rowId > 0)

            // 2. The same envelope twice (a retry after a lost answer): gomuks runs it once. (Its
            // exec buffer answers the repeat with an empty result — a gomuks bug: the entry is
            // stored by value — so the outbox treats any Ok as accepted and lets sync show it.)
            val start = System.currentTimeMillis()
            val txn = "xmuks-live-$start"
            val body = "xmuks retry test $start"
            val first = exec.execOnce("send_message", params(body), txn, start)
            val again = exec.execOnce("send_message", params(body), txn, start)
            println("retry: first=$first\nretry: repeat=$again")
            assertTrue(first is ExecResult.Ok && again is ExecResult.Ok)
            val firstRow = GomuksJson.decodeFromJsonElement(Event.serializer(), (first as ExecResult.Ok).data).rowId

            // 3. Both went out, and the retried one is in the room exactly once.
            delay(4_000)
            val page =
                exec.exec(
                    "paginate",
                    buildJsonObject {
                        put("room_id", JsonPrimitive(room))
                        put("max_timeline_id", JsonPrimitive(0))
                        put("limit", JsonPrimitive(20))
                    },
                    ExecMode.Read,
                ) as ExecResult.Ok
            val events = GomuksJson.decodeFromJsonElement(PaginationResponse.serializer(), page.data).events
            for (row in listOf(echo.rowId, firstRow)) {
                val sent = events.firstOrNull { it.rowId == row }
                assertNotNull("rowid $row in the room", sent)
                println("rowid $row → ${sent!!.eventId} send_error=${sent.sendError}")
                assertTrue(sent.eventId.startsWith("$"))
            }
            val copies = events.count { (it.content["body"] as? JsonPrimitive)?.content == body }
            println("copies of the retried message in the room: $copies")
            assertEquals(1, copies)
            db.close()
        }
}
