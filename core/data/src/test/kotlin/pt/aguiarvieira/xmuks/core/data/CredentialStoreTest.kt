package pt.aguiarvieira.xmuks.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SecretCipher
import pt.aguiarvieira.xmuks.core.network.Credentials

class CredentialStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher; [broken] simulates a lost Keystore key. */
    private class XorCipher(
        var broken: Boolean = false,
    ) : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(sealed: ByteArray): ByteArray {
            check(!broken) { "key lost" }
            return encrypt(sealed)
        }
    }

    private val file by lazy { tmp.newFile("session.preferences_pb").also { it.delete() } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun store(cipher: SecretCipher) = CredentialStore(PreferenceDataStoreFactory.create { file }, cipher, scope)

    private val creds = Credentials("https://gomuks.example.org/".toHttpUrl(), "alice", "hunter2")

    @Test
    fun `secrets are not stored in the clear`() =
        runBlocking {
            store(XorCipher()).saveLogin(creds, "hunter2-token")
            val onDisk = file.readText(Charsets.ISO_8859_1)
            assertFalse(onDisk.contains("hunter2"))
            assertTrue("non-secret fields stay readable", onDisk.contains("gomuks.example.org"))
        }

    @Test
    fun `load restores credentials and token`() =
        runBlocking {
            val cipher = XorCipher()
            val dataStore = PreferenceDataStoreFactory.create { file }
            CredentialStore(dataStore, cipher, scope).saveLogin(creds, "tok")
            val again = CredentialStore(dataStore, cipher, scope)
            again.load()
            assertEquals(creds, again.credentials())
            assertEquals("tok", again.token())
            assertTrue(again.loggedIn.value)
        }

    @Test
    fun `an undecryptable password means logged out`() =
        runBlocking {
            val cipher = XorCipher()
            val dataStore = PreferenceDataStoreFactory.create { file }
            CredentialStore(dataStore, cipher, scope).saveLogin(creds, "tok")
            cipher.broken = true
            val again = CredentialStore(dataStore, cipher, scope)
            again.load()
            assertNull(again.credentials())
            assertFalse(again.loggedIn.value)
        }
}
