package pt.aguiarvieira.xmuks.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * HTTP clients for gomuks: plain (login), api (auth + compression), sse (long read timeout).
 * Shared by every account: a request carries the login of the account whose server it goes to.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_S = 15L

    /** Some commands (pagination, media) legitimately take a while. */
    private const val READ_TIMEOUT_S = 60L

    /** gomuks pings `/sse` every ~15 s; three missed pings means the stream is dead. */
    private const val SSE_READ_TIMEOUT_S = 45L

    /** No auth interceptor: used only to log in (and by the interceptor to refresh the token). */
    @Provides @Singleton
    @Named("plain")
    fun plainHttp(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun authApi(
        @Named("plain") plain: OkHttpClient,
    ) = AuthApi(plain)

    @Provides @Singleton
    @Named("api")
    fun apiHttp(
        @Named("plain") plain: OkHttpClient,
        registry: AccountRegistry,
        authApi: AuthApi,
    ): OkHttpClient =
        plain
            .newBuilder()
            .addInterceptor(CompressionInterceptor())
            .addInterceptor(AuthInterceptor(registry::sessionFor, authApi))
            .build()

    /**
     * Media streaming (video/audio): auth only. No compression negotiation — players make Range
     * requests, and byte ranges of a compressed body are meaningless.
     */
    @Provides @Singleton
    @Named("media")
    fun mediaHttp(
        @Named("plain") plain: OkHttpClient,
        registry: AccountRegistry,
        authApi: AuthApi,
    ): OkHttpClient = plain.newBuilder().addInterceptor(AuthInterceptor(registry::sessionFor, authApi)).build()

    @Provides @Singleton
    @Named("sse")
    fun sseHttp(
        @Named("api") api: OkHttpClient,
    ): OkHttpClient = api.newBuilder().readTimeout(SSE_READ_TIMEOUT_S, TimeUnit.SECONDS).build()
}
