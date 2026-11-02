package pt.aguiarvieira.xmuks.core.data.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthResult
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.isOnLocalNetwork
import pt.aguiarvieira.xmuks.core.network.parseServerUrl

sealed interface LoginResult {
    data object Success : LoginResult

    data object InvalidUrl : LoginResult

    data object InsecureUrl : LoginResult

    data object BadCredentials : LoginResult

    data class Failed(
        val reason: String,
    ) : LoginResult
}

/** Signing accounts in and out. A sign-in adds an account and shows it; a sign-out ends only that one. */
class SessionRepository(
    private val registry: AccountRegistry,
    private val authApi: AuthApi,
    private val io: CoroutineDispatcher,
    scope: CoroutineScope,
    /** What's kept for an account, wiped when it signs out (or its ID is reused). */
    private val accountScoped: (id: String) -> Set<AccountScoped>,
) {
    /** Some account is signed in (and shown). */
    val loggedIn: StateFlow<Boolean> =
        registry.active.map { it != null }.stateIn(scope, SharingStarted.Eagerly, registry.active.value != null)

    suspend fun login(
        server: String,
        username: String,
        password: String,
    ): LoginResult {
        val url = parseServerUrl(server) ?: return LoginResult.InvalidUrl
        val credentials = Credentials(url, username.trim(), password)
        return when (val result = withContext(io) { authApi.login(credentials) }) {
            is AuthResult.Success -> {
                val id = registry.nextId()
                // Also on login, not just logout: nothing from a previous account may leak into this one.
                clearAccountData(id)
                registry.signIn(id, credentials, result.token)
                LoginResult.Success
            }

            AuthResult.InsecureTransport -> {
                LoginResult.InsecureUrl
            }

            AuthResult.BadCredentials -> {
                LoginResult.BadCredentials
            }

            is AuthResult.ServerError -> {
                LoginResult.Failed("Server answered ${result.code}: ${result.message}")
            }

            is AuthResult.NetworkError -> {
                LoginResult.Failed(result.cause.message ?: result.cause.javaClass.simpleName)
            }
        }
    }

    /** Whether [server], as typed, is on the local network (see [LocalNetworkPermission]). */
    suspend fun isLocalServer(server: String): Boolean {
        val url = parseServerUrl(server) ?: return false
        return withContext(io) { url.isOnLocalNetwork() }
    }

    /** The same, for the server of the account shown. */
    suspend fun isLocalServer(): Boolean {
        val id = registry.active.value ?: return false
        val url = registry.store(id).credentials()?.serverUrl ?: return false
        return withContext(io) { url.isOnLocalNetwork() }
    }

    /**
     * Signs the shown account out and forgets everything cached for it; the next account (if any)
     * is shown. It stops being shown (its stream stops) before its login goes.
     */
    suspend fun logout() {
        val id = registry.active.value ?: return
        accountScoped(id).forEach { it.beforeLogout() }
        registry.signOut(id)
        clearAccountData(id)
    }

    private suspend fun clearAccountData(id: String) = accountScoped(id).forEach { it.clearAccountData() }
}
