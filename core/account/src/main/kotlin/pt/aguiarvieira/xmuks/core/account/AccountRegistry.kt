package pt.aguiarvieira.xmuks.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.SessionStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The gomuks accounts signed in on this install, and which one the app shows ([active]). Each has
 * a local ID (not its Matrix ID: two gomuks could be the same Matrix user) naming its own files
 * ([fileName]) and its own [CredentialStore].
 *
 * The account from before there could be several is [LEGACY]: its files keep their old names, so
 * it carries on signed in, nothing moved.
 */
class AccountRegistry(
    private val dataStore: DataStore<Preferences>,
    /** The DataStore an account's login lives in. Called once per ID. */
    private val sessionStore: (id: String) -> DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val scope: CoroutineScope,
) {
    private val stores = ConcurrentHashMap<String, CredentialStore>()
    private val lock = Mutex()

    private val _ids = MutableStateFlow<List<String>>(emptyList())

    /** Signed-in accounts, in the order they were added. */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)

    /** The account on screen; null when none is signed in. */
    val active: StateFlow<String?> = _active.asStateFlow()

    /** The ID the next sign-in gets (stable until it's used, so what's built for it is reused). */
    @Volatile private var pending: String? = null

    /** Loads the accounts and their logins; call once before the first network request. */
    suspend fun load() {
        val prefs = dataStore.data.first()
        val saved = prefs[IDS]?.split(SEPARATOR)?.filter(String::isNotBlank).orEmpty()
        saved.forEach { load(it) }
        val ids = saved.filter { stores.getValue(it).credentials() != null }.toMutableList()
        // From before accounts: the one login there was, in the old files.
        if (LEGACY !in ids && load(LEGACY).credentials() != null) ids.add(0, LEGACY)
        _ids.value = ids
        _active.value = prefs[ACTIVE]?.takeIf { it in ids } ?: ids.firstOrNull()
        if (ids != saved || prefs[ACTIVE] != _active.value) persist()
    }

    private suspend fun load(id: String) = store(id).also { it.load() }

    /** [id]'s login (empty for an ID not signed in). */
    fun store(id: String): CredentialStore = stores.getOrPut(id) { CredentialStore(sessionStore(id), cipher, scope) }

    /** The signed-in account that is [credentials]' user on [credentials]' gomuks, if any. */
    fun idFor(credentials: Credentials): String? =
        _ids.value.firstOrNull { id ->
            store(id).credentials()?.let {
                it.serverUrl == credentials.serverUrl && it.username == credentials.username
            } == true
        }

    /** The ID a new sign-in will use: the old files' while they're free, else a fresh one. */
    fun nextId(): String =
        pending ?: synchronized(this) {
            pending ?: (if (LEGACY in _ids.value) newId() else LEGACY).also { pending = it }
        }

    /** [id] signed in as [credentials]: added (if new) and shown. */
    suspend fun signIn(
        id: String,
        credentials: Credentials,
        token: String,
    ) = lock.withLock {
        store(id).saveLogin(credentials, token)
        if (id !in _ids.value) _ids.value += id
        if (pending == id) pending = null
        _active.value = id
        persist()
    }

    /** Shows [id] (one already signed in). */
    suspend fun activate(id: String) =
        lock.withLock {
            if (id !in _ids.value || _active.value == id) return@withLock
            _active.value = id
            persist()
        }

    /**
     * [id] signed out: no longer listed, and if it was shown, the next one is (or none). Its login
     * is forgotten last, once nothing streams as it any more.
     */
    suspend fun signOut(id: String) {
        lock.withLock {
            _ids.value -= id
            if (_active.value == id) _active.value = _ids.value.firstOrNull()
            persist()
        }
        store(id).clear()
    }

    /**
     * The login a request to [url] goes out with: the account whose gomuks it is. Two behind the
     * same host are told apart by path (the longest server path [url] starts with).
     */
    fun sessionFor(url: HttpUrl): SessionStore? =
        stores.values
            .mapNotNull { store -> store.credentials()?.serverUrl?.let { store to it } }
            .filter { (_, server) ->
                server.scheme == url.scheme && server.host == url.host && server.port == url.port &&
                    url.encodedPath.startsWith(server.encodedPath.trimEnd('/'))
            }.maxByOrNull { (_, server) -> server.encodedPath.length }
            ?.first

    private suspend fun persist() {
        val ids = _ids.value.joinToString(SEPARATOR)
        val active = _active.value
        dataStore.edit {
            it[IDS] = ids
            if (active == null) it.remove(ACTIVE) else it[ACTIVE] = active
        }
    }

    private fun newId() = UUID.randomUUID().toString().take(ID_LENGTH)

    companion object {
        /** The account from before there could be several: its files keep their names. */
        const val LEGACY = "main"
        private const val SEPARATOR = ","
        private const val ID_LENGTH = 8
        private val IDS = stringPreferencesKey("ids")
        private val ACTIVE = stringPreferencesKey("active")

        /** [id]'s own copy of a file named [base] (+ [extension]). The legacy account's is [base] itself. */
        fun fileName(
            id: String,
            base: String,
            extension: String = "",
        ) = if (id == LEGACY) "$base$extension" else "$base-$id$extension"
    }
}
