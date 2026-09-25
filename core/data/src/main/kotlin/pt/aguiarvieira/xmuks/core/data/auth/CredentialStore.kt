package pt.aguiarvieira.xmuks.core.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.SessionStore
import java.util.Base64

/**
 * Login state: server URL and username in the clear, password and session token encrypted with
 * [cipher]. Reads are served from memory (OkHttp interceptors call them synchronously on network
 * threads); writes update memory first, then persist in the background.
 */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val scope: CoroutineScope,
) : SessionStore {
    private data class State(
        val credentials: Credentials? = null,
        val token: String? = null,
    )

    private val state = MutableStateFlow(State())
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** Loads persisted state; call once before the first network request. */
    suspend fun load() {
        val prefs = dataStore.data.first()
        val server = prefs[SERVER]?.toHttpUrlOrNull()
        val user = prefs[USERNAME]
        val password = prefs[PASSWORD]?.let(::open)
        val credentials =
            if (server != null && user != null &&
                password != null
            ) {
                Credentials(server, user, password)
            } else {
                null
            }
        state.value = State(credentials, prefs[TOKEN]?.let(::open))
        _loggedIn.value = credentials != null
    }

    override fun credentials(): Credentials? = state.value.credentials

    override fun token(): String? = state.value.token

    override fun saveToken(token: String?) {
        state.value = state.value.copy(token = token)
        scope.launch {
            dataStore.edit { if (token == null) it.remove(TOKEN) else it[TOKEN] = seal(token) }
        }
    }

    suspend fun saveLogin(
        credentials: Credentials,
        token: String,
    ) {
        state.value = State(credentials, token)
        dataStore.edit {
            it[SERVER] = credentials.serverUrl.toString()
            it[USERNAME] = credentials.username
            it[PASSWORD] = seal(credentials.password)
            it[TOKEN] = seal(token)
        }
        _loggedIn.value = true
    }

    suspend fun clear() {
        state.value = State()
        dataStore.edit { it.clear() }
        _loggedIn.value = false
    }

    private fun seal(value: String) = Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray()))

    /** Undecryptable (e.g. the Keystore key is gone) reads as absent: the user logs in again. */
    private fun open(value: String): String? =
        runCatching { String(cipher.decrypt(Base64.getDecoder().decode(value))) }.getOrNull()

    private companion object {
        val SERVER = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_sealed")
        val TOKEN = stringPreferencesKey("token_sealed")
    }
}
