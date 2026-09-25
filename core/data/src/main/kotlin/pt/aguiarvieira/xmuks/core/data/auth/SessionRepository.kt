package pt.aguiarvieira.xmuks.core.data.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthResult
import pt.aguiarvieira.xmuks.core.network.Credentials
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

    suspend fun logout() = store.clear()
}
