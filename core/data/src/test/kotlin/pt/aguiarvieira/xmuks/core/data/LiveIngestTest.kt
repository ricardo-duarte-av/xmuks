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
            if (System.getenv("XMUKS_LIVE_DUMP") != null) dumpRoomList(db)
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

    /** Local diagnostics only (XMUKS_LIVE_DUMP=1): what the room list would show for real data. */
    private fun dumpRoomList(db: XmuksDatabase) =
        runBlocking {
            val repo =
                pt.aguiarvieira.xmuks.core.data.rooms
                    .RoomListRepository(
                        db,
                        pt.aguiarvieira.xmuks.core.data.media
                            .MediaUrls { server }
                    )
            val chats = repo.chats().first()
            val dms = repo.directMessages().first()
            val spaces = repo.topLevelSpaces().first()
            println("chats=${chats.size} dms=${dms.size} spaces=${spaces.size}")
            println(
                "chats without preview: ${chats.count {
                    it.preview == pt.aguiarvieira.xmuks.core.data.rooms.Preview.None
                }}"
            )
            println("previews with raw MXID sender: ${chats.count { it.previewSender?.startsWith("@") == true }}")
            println("rooms named by ID: ${(chats + dms).count { it.name.startsWith("!") }}")
            println("with avatar: ${(chats + dms).count { it.avatarUrl != null }}/${chats.size + dms.size}")
            val rows = db.roomListDao().chats().first()
            println(
                "no-preview by type: " +
                    rows
                        .filter {
                            it.previewText.isNullOrBlank()
                        }.groupingBy { it.previewType ?: "<no event>" }
                        .eachCount()
            )
            chats.take(8).forEach {
                println(
                    "  ${it.name.take(
                        24
                    )} | ${it.previewSender?.take(
                        16
                    )}: ${(it.preview as? pt.aguiarvieira.xmuks.core.data.rooms.Preview.Text)?.text?.take(
                        30
                    )} | ${it.unread}"
                )
            }
            spaces.take(6).forEach { println("  [space] ${it.name.take(24)} rooms=${it.rooms} ${it.unread}") }
        }
}
