package pt.aguiarvieira.xmuks.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Sends the session token as the `gomuks_auth` cookie. On a 401 the token is dropped, a fresh one
 * is fetched with the stored password (over HTTPS only, via [AuthApi]) and the request is retried
 * once. A 401 means the command never ran, so the retry can't double-execute it.
 */
class AuthInterceptor(
    private val store: SessionStore,
    private val authApi: AuthApi,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.token() ?: refresh()
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != HTTP_UNAUTHORIZED) return response
        response.close()
        store.saveToken(null)
        val fresh = refresh() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withToken(fresh))
    }

    @Synchronized
    private fun refresh(): String? {
        store.token()?.let { return it } // another thread refreshed while we waited
        val credentials = store.credentials() ?: return null
        val result = authApi.login(credentials)
        return (result as? AuthResult.Success)?.token?.also(store::saveToken)
    }

    private fun Request.withToken(token: String?): Request =
        if (token == null) this else newBuilder().header("Cookie", "gomuks_auth=$token").build()
}
