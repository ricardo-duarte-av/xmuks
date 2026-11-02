package pt.aguiarvieira.xmuks.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Sends the session token as the `gomuks_auth` cookie — to our gomuks servers only: each request
 * gets the token of the account whose server it goes to ([sessions]), and requests anywhere else
 * (a map tile, any image that isn't gomuks media) go out untouched, token-free. On
 * a 401 the token is dropped, a fresh one is fetched with the stored password (over HTTPS only,
 * via [AuthApi]) and the request is retried once. A 401 means the command never ran, so the retry
 * can't double-execute it.
 */
class AuthInterceptor(
    /** The login for a request's URL: the account whose server it is, if any. */
    private val sessions: (HttpUrl) -> SessionStore?,
    private val authApi: AuthApi,
) : Interceptor {
    /** One account only. */
    constructor(store: SessionStore, authApi: AuthApi) : this({ store }, authApi)

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val store = sessions(url)?.takeIf { url.isServerOf(it) } ?: return chain.proceed(chain.request())
        val token = store.token() ?: refresh(store)
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != HTTP_UNAUTHORIZED) return response
        response.close()
        store.saveToken(null)
        val fresh = refresh(store) ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().withToken(fresh))
    }

    @Synchronized
    private fun refresh(store: SessionStore): String? {
        store.token()?.let { return it } // another thread refreshed while we waited
        val credentials = store.credentials() ?: return null
        val result = authApi.login(credentials)
        return (result as? AuthResult.Success)?.token?.also(store::saveToken)
    }

    private fun HttpUrl.isServerOf(store: SessionStore): Boolean {
        val server = store.credentials()?.serverUrl ?: return false
        return scheme == server.scheme && host == server.host && port == server.port
    }

    private fun Request.withToken(token: String?): Request =
        if (token == null) this else newBuilder().header("Cookie", "gomuks_auth=$token").build()
}
