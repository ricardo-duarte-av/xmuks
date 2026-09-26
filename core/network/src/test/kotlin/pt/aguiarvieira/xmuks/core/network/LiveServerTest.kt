package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import java.util.concurrent.TimeUnit

/**
 * End-to-end against a real gomuks, read-only: log in, take the full sync, reconnect with the resume
 * point and expect a catch-up. Opt-in, never in CI:
 *
 *     XMUKS_LIVE_SERVER=https://… XMUKS_LIVE_USER=… XMUKS_LIVE_PASS=… \
 *         ./gradlew :core:network:testDebugUnitTest --tests '*LiveServerTest*'
 */
class LiveServerTest {
    private val server = System.getenv("XMUKS_LIVE_SERVER")?.let(::parseServerUrl)
    private val user = System.getenv("XMUKS_LIVE_USER")
    private val pass = System.getenv("XMUKS_LIVE_PASS")

    @Test
    fun `full sync then catch-up against a live server`() {
        assumeTrue("set XMUKS_LIVE_* to run", server != null && user != null && pass != null)
        val plain = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()
        val store = FakeSessionStore(Credentials(server!!, user!!, pass!!))
        val http =
            plain
                .newBuilder()
                .addInterceptor(
                    CompressionInterceptor()
                ).addInterceptor(AuthInterceptor(store, AuthApi(plain)))
                .build()
        val resume =
            object : ResumeStore {
                var point = ResumePoint()

                override suspend fun load() = point

                override suspend fun save(point: ResumePoint) {
                    this.point = point
                }
            }

        suspend fun session(label: String): Pair<Int, Boolean> {
            var rooms = 0
            var catchup = false
            val start = System.nanoTime()
            val connection =
                GomuksConnection(SseClient(http, { server }, io), http, { server }, resume, io) { frame ->
                    (frame.event as? GomuksEvent.Sync)?.let {
                        rooms += it.sync.rooms.size
                        catchup = catchup || it.sync.catchup
                        // Stand-in for the database store: this test applies nothing, so it just
                        // records the snapshot's timestamp to request a catch-up next time.
                        resume.point =
                            resume.point.copy(lastServerTs = maxOf(resume.point.lastServerTs, it.sync.serverTimestamp))
                    }
                }
            runBlocking {
                val job = async { connection.run() }
                val watcher =
                    async {
                        connection.state.collect {
                            println(
                                "  [${(System.nanoTime() - start) / 1_000_000} ms] $it"
                            )
                        }
                    }
                withTimeout(120_000) { connection.state.first { it == ConnectionState.Live } }
                job.cancel()
                watcher.cancel()
            }
            println(
                "$label: $rooms rooms (catchup=$catchup) live after ${(System.nanoTime() - start) / 1_000_000} ms, " +
                    "resume=${resume.point}"
            )
            return rooms to catchup
        }

        runBlocking {
            val (fullRooms, fullCatchup) = session("full")
            assertTrue(fullRooms > 0)
            assertTrue(!fullCatchup)
            // New connection = new run of the loop; the backend run is the same, so it resumes or catches up.
            val (_, _) = session("reconnect")
            assertTrue(resume.point.lastServerTs > 0)
        }
    }
}
