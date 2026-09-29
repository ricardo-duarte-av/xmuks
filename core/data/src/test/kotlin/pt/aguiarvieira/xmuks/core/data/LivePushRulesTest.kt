package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.SessionStore
import pt.aguiarvieira.xmuks.core.network.parseServerUrl
import java.util.concurrent.TimeUnit

/**
 * Opt-in, and it WRITES push rules for [XMUKS_LIVE_PUSH_ROOM] (a room of your own), leaving it on
 * Default at the end.
 *
 *     XMUKS_LIVE_SERVER=… XMUKS_LIVE_USER=… XMUKS_LIVE_PASS=… XMUKS_LIVE_PUSH_ROOM='!room:server' \
 *         ./gradlew :core:data:testDebugUnitTest --tests '*LivePushRulesTest*' -i
 */
@RunWith(AndroidJUnit4::class)
class LivePushRulesTest {
    private val server = System.getenv("XMUKS_LIVE_SERVER")?.let(::parseServerUrl)
    private val user = System.getenv("XMUKS_LIVE_USER")
    private val pass = System.getenv("XMUKS_LIVE_PASS")
    private val room = System.getenv("XMUKS_LIVE_PUSH_ROOM")

    @Test
    fun `every setting can be written`() =
        runBlocking {
            assumeTrue(server != null && user != null && pass != null && room != null)
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
            val db = XmuksDatabase.build(ApplicationProvider.getApplicationContext(), name = null, driver = AndroidSQLiteDriver())
            val rules = RoomPushRules(db, ExecClient(http, { server }, Dispatchers.IO))
            RoomNotifications.entries.reversed().forEach { setting ->
                assertNull(setting.name, rules.set(room!!, setting))
            }
        }
}
