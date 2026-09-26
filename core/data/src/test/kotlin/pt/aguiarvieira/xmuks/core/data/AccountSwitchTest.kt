package pt.aguiarvieira.xmuks.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.LoginResult
import pt.aguiarvieira.xmuks.core.data.auth.SecretCipher
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.InMemoryResumeStore
import pt.aguiarvieira.xmuks.core.data.connection.SyncSummary
import pt.aguiarvieira.xmuks.core.data.connection.SyncSummarySink
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthResult
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.protocol.FrameDecoder

/** Regression: logging out of one gomuks and into another kept the first account's rooms and resume point. */
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
            val sink = SyncSummarySink()
            val resume = InMemoryResumeStore()
            val store =
                CredentialStore(
                    PreferenceDataStoreFactory.create { tmp.newFile("s.preferences_pb").also { it.delete() } },
                    PlainCipher,
                    CoroutineScope(SupervisorJob() + Dispatchers.IO),
                )
            val session = SessionRepository(store, AcceptAll, Dispatchers.IO, setOf(sink, resume))

            assertEquals(LoginResult.Success, session.login("testmuks.example.org", "alice", "pw"))
            sink.accept(FrameDecoder.decode(ROOMS_LINE)!!)
            resume.save(ResumePoint("run-a", -5, 1, 1_000))
            assertEquals(2, sink.summary.value.rooms)

            session.logout()
            assertEquals(SyncSummary(), sink.summary.value)
            assertEquals(ResumePoint(), resume.load())

            // Frames still in flight from the old stream after logout must not leak into the next account.
            sink.accept(FrameDecoder.decode(ROOMS_LINE)!!)
            assertEquals(LoginResult.Success, session.login("webmuks.example.org", "alice", "pw"))
            assertEquals(SyncSummary(), sink.summary.value)
            assertEquals("webmuks.example.org", store.credentials()!!.serverUrl.host)
        }

    private companion object {
        const val ROOMS_LINE =
            """{"command":"sync_complete","request_id":0,"data":{"server_timestamp":1000,"clear_state":true,""" +
                """"rooms":{"!a:x":{"meta":{"room_id":"!a:x"}},"!b:x":{"meta":{"room_id":"!b:x"}}}}}"""
    }
}
