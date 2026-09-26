package pt.aguiarvieira.xmuks.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.LoginResult
import pt.aguiarvieira.xmuks.core.data.auth.SecretCipher
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.StreamStats
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthResult
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.protocol.FrameDecoder

/** Regression: logging out of one gomuks and into another kept the first account's rooms and resume point. */
@RunWith(AndroidJUnit4::class)
class AccountSwitchTest {
    @get:Rule val tmp = TemporaryFolder()

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain

        override fun decrypt(sealed: ByteArray) = sealed
    }

    private object AcceptAll : AuthApi(OkHttpClient()) {
        override fun login(credentials: Credentials) = AuthResult.Success("token-for-${credentials.serverUrl.host}")
    }

    @Test
    fun `logging in to another account starts from nothing`() =
        runBlocking {
            val db =
                XmuksDatabase.build(
                    ApplicationProvider.getApplicationContext(),
                    name = null,
                    driver = AndroidSQLiteDriver()
                )
            val ingestor = SyncIngestor(db)
            val stats = StreamStatsTracker()
            val store =
                CredentialStore(
                    PreferenceDataStoreFactory.create { tmp.newFile("s.preferences_pb").also { it.delete() } },
                    PlainCipher,
                    CoroutineScope(SupervisorJob() + Dispatchers.IO),
                )
            val session = SessionRepository(store, AcceptAll, Dispatchers.IO, setOf(ingestor, stats))

            suspend fun roomCount() =
                db
                    .roomListDao()
                    .counts()
                    .first()
                    .rooms

            assertEquals(LoginResult.Success, session.login("testmuks.example.org", "alice", "pw"))
            ingestor.apply(FrameDecoder.decode(ROOMS_LINE)!!)
            ingestor.apply(FrameDecoder.decode(INIT_LINE)!!)
            ingestor.save(ResumePoint("run-a", -5, 1, 0))
            assertEquals(2, roomCount())
            assertEquals(1_000L, ingestor.load().lastServerTs)

            session.logout()
            assertEquals(0, roomCount())
            assertEquals("the next server must not be asked for a catch-up", ResumePoint(), ingestor.load())
            assertEquals(StreamStats(), stats.stats.value)

            // Frames still in flight from the old stream after logout must not leak into the next account.
            ingestor.apply(FrameDecoder.decode(ROOMS_LINE)!!)
            assertEquals(LoginResult.Success, session.login("webmuks.example.org", "alice", "pw"))
            assertEquals(0, roomCount())
            assertEquals("webmuks.example.org", store.credentials()!!.serverUrl.host)
            db.close()
        }

    private companion object {
        const val ROOMS_LINE =
            """{"command":"sync_complete","request_id":0,"data":{"server_timestamp":1000,"clear_state":true,""" +
                """"rooms":{"!a:x":{"meta":{"room_id":"!a:x"}},"!b:x":{"meta":{"room_id":"!b:x"}}}}}"""
        const val INIT_LINE = """{"command":"init_complete","request_id":0,"data":{}}"""
    }
}
