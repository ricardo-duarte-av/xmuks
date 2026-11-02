package pt.aguiarvieira.xmuks.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.aguiarvieira.xmuks.core.network.Credentials

class AccountRegistryTest {
    @get:Rule val tmp = TemporaryFolder()

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain

        override fun decrypt(sealed: ByteArray) = sealed
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One DataStore per file for the whole test, as in the app (two on one file is an error). */
    private val dataStores = HashMap<String, DataStore<Preferences>>()

    private fun dataStore(name: String) =
        dataStores.getOrPut(name) {
            PreferenceDataStoreFactory.create { tmp.root.resolve("$name.preferences_pb") }
        }

    private fun registry() =
        AccountRegistry(
            dataStore("accounts"),
            { id -> dataStore(AccountRegistry.fileName(id, "session")) },
            PlainCipher,
            scope,
        )

    private val alice = Credentials("https://gomuks.example.org/".toHttpUrl(), "alice", "pw")
    private val bob = Credentials("https://other.example.org/gomuks/".toHttpUrl(), "bob", "pw")

    @Test
    fun `the login from before accounts carries on as the legacy account`() =
        runBlocking {
            CredentialStore(dataStore("session"), PlainCipher, scope).saveLogin(alice, "tok")
            val registry = registry().also { it.load() }
            assertEquals(listOf(AccountRegistry.LEGACY), registry.ids.value)
            assertEquals(AccountRegistry.LEGACY, registry.active.value)
            assertEquals(alice, registry.store(AccountRegistry.LEGACY).credentials())
        }

    @Test
    fun `a second account gets its own files and is shown`() =
        runBlocking {
            val registry = registry().also { it.load() }
            assertNull(registry.active.value)
            val first = registry.nextId()
            assertEquals(AccountRegistry.LEGACY, first)
            registry.signIn(first, alice, "a")
            val second = registry.nextId()
            assertNotEquals(first, second)
            assertEquals("asked again before use, the same ID", second, registry.nextId())
            registry.signIn(second, bob, "b")
            assertEquals(listOf(first, second), registry.ids.value)
            assertEquals(second, registry.active.value)
            assertEquals("session-$second", AccountRegistry.fileName(second, "session"))

            val again = registry().also { it.load() }
            assertEquals(listOf(first, second), again.ids.value)
            assertEquals(second, again.active.value)
            assertEquals(bob, again.store(second).credentials())
        }

    @Test
    fun `signing out shows the next account`() =
        runBlocking {
            val registry = registry().also { it.load() }
            val first = registry.nextId().also { registry.signIn(it, alice, "a") }
            val second = registry.nextId().also { registry.signIn(it, bob, "b") }
            registry.signOut(second)
            assertEquals(listOf(first), registry.ids.value)
            assertEquals(first, registry.active.value)
            assertNull(registry.store(second).credentials())
            registry.signOut(first)
            assertNull(registry.active.value)
            assertEquals("the old files are free again", AccountRegistry.LEGACY, registry.nextId())
        }

    @Test
    fun `requests carry the login of the account whose server they go to`() =
        runBlocking {
            val registry = registry().also { it.load() }
            val first = registry.nextId().also { registry.signIn(it, alice, "a") }
            val second = registry.nextId().also { registry.signIn(it, bob, "b") }
            assertSame(
                registry.store(first),
                registry.sessionFor("https://gomuks.example.org/_gomuks/exec/x".toHttpUrl()),
            )
            assertSame(
                registry.store(second),
                registry.sessionFor("https://other.example.org/gomuks/_gomuks/media/a/b".toHttpUrl()),
            )
            assertNull(registry.sessionFor("https://other.example.org/elsewhere".toHttpUrl()))
            assertNull(registry.sessionFor("https://tiles.example.org/1/2/3.png".toHttpUrl()))
        }
}
