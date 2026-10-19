package pt.aguiarvieira.xmuks.core.data.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.account.CredentialStore
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

class SessionRepository(
    private val store: CredentialStore,
    private val authApi: AuthApi,
    private val io: CoroutineDispatcher,
    private val accountScoped: Set<AccountScoped>,
) {
    val loggedIn = store.loggedIn

    suspend fun login(
        server: String,
        username: String,
        password: String,
    ): LoginResult {
        val url = parseServerUrl(server) ?: return LoginResult.InvalidUrl
        val credentials = Credentials(url, username.trim(), password)
        return when (val result = withContext(io) { authApi.login(credentials) }) {
            is AuthResult.Success -> {
                // Also on login, not just logout: nothing from a previous account may leak into this one.
                clearAccountData()
                store.saveLogin(credentials, result.token)
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

    /** The same, for the server we're logged in to. */
    suspend fun isLocalServer(): Boolean {
        val url = store.credentials()?.serverUrl ?: return false
        return withContext(io) { url.isOnLocalNetwork() }
    }

    /** Forgets the account and everything cached for it. Flipping [loggedIn] stops the stream first. */
    suspend fun logout() {
        accountScoped.forEach { it.beforeLogout() }
        store.clear()
        clearAccountData()
    }

    private suspend fun clearAccountData() = accountScoped.forEach { it.clearAccountData() }
}
