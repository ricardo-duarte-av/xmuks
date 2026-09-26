package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.SessionStore
import pt.aguiarvieira.xmuks.core.network.SseClient
import pt.aguiarvieira.xmuks.core.network.parseServerUrl
import java.util.concurrent.TimeUnit

/**
 * Opt-in, read-only: a real gomuks stream into a real database — proves real payloads fit the schema,
 * that a "restart" (new ingestor, same file) resumes with a catch-up, and how long ingestion takes.
 *
 *     XMUKS_LIVE_SERVER=https://… XMUKS_LIVE_USER=… XMUKS_LIVE_PASS=… \
 *         ./gradlew :core:data:testDebugUnitTest --tests '*LiveIngestTest*' -i
 */
@RunWith(AndroidJUnit4::class)
class LiveIngestTest {
    private val server = System.getenv("XMUKS_LIVE_SERVER")?.let(::parseServerUrl)
    private val user = System.getenv("XMUKS_LIVE_USER")
    private val pass = System.getenv("XMUKS_LIVE_PASS")

    @Test
    fun `live stream into the database, then resume after a restart`() {
        assumeTrue(server != null && user != null && pass != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("live.db")
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
        val http =
            plain
                .newBuilder()
                .addInterceptor(
                    CompressionInterceptor()
                ).addInterceptor(AuthInterceptor(session, AuthApi(plain)))
                .build()

        fun run(label: String): Pair<Int, Boolean> {
            val db = XmuksDatabase.build(context, "live.db", AndroidSQLiteDriver())
            val ingestor = SyncIngestor(db)
            var catchup = false
            val connection =
                GomuksConnection(
                    SseClient(http, { server }, Dispatchers.IO),
                    http,
                    { server },
                    ingestor,
                    Dispatchers.IO
                ) {
                    if ((it.event as? pt.aguiarvieira.xmuks.core.protocol.GomuksEvent.Sync)?.sync?.catchup ==
                        true
                    ) {
                        catchup = true
                    }
                    ingestor.apply(it)
                }
            val start = System.nanoTime()
            val rooms =
                runBlocking {
                    val job = async { connection.run() }
                    withTimeout(120_000) { connection.state.first { it == ConnectionState.Live } }
                    job.cancel()
                    db.roomListDao().counts().first()
                }
            println("$label: live+ingested in ${(System.nanoTime() - start) / 1_000_000} ms, catchup=$catchup, $rooms")
            db.close()
            return rooms.rooms + rooms.spaces to catchup
        }

        val (first, firstCatchup) = run("cold (empty db)")
        assertTrue(first > 0)
        assertTrue("an empty database must get a full snapshot", !firstCatchup)
        val (second, secondCatchup) = run("restart (same db)")
        assertTrue("a restart with an intact cache must catch up, not re-download", secondCatchup)
        assertEquals(first, second)
    }
}
