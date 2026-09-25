package pt.aguiarvieira.xmuks.core.network

import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class FakeSessionStore(
    var creds: Credentials?,
    var currentToken: String? = null,
) : SessionStore {
    override fun credentials() = creds

    override fun token() = currentToken

    override fun saveToken(token: String?) {
        currentToken = token
    }
}

/** The production interceptor stack, pointed at a test server. */
internal fun testClient(
    store: SessionStore,
    readTimeoutS: Long = 5,
): OkHttpClient {
    val plain = OkHttpClient.Builder().readTimeout(readTimeoutS, TimeUnit.SECONDS).build()
    return plain
        .newBuilder()
        .addInterceptor(CompressionInterceptor())
        .addInterceptor(AuthInterceptor(store, AuthApi(plain)))
        .build()
}

internal val io = Dispatchers.IO

/** Test servers are plain HTTP; AuthApi refuses Basic over HTTP, so tests pre-seed a token. */
internal fun credsFor(url: HttpUrl) = Credentials(url, "alice", "secret")
