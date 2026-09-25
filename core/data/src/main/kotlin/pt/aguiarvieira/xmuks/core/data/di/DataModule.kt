package pt.aguiarvieira.xmuks.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.InMemoryResumeStore
import pt.aguiarvieira.xmuks.core.data.connection.SyncSummarySink
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.SseClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val CONNECT_TIMEOUT_S = 15L

    /** Some commands (pagination, media) legitimately take a while. */
    private const val READ_TIMEOUT_S = 60L

    /** gomuks pings `/sse` every ~15 s; three missed pings means the stream is dead. */
    private const val SSE_READ_TIMEOUT_S = 45L

    @Provides @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun credentialStore(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ): CredentialStore {
        val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }
        // One small file read, before anything can issue a request: network code reads it synchronously.
        return CredentialStore(
            dataStore,
            KeystoreSecretCipher(),
            scope
        ).also { runBlocking(Dispatchers.IO) { it.load() } }
    }

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
        store: CredentialStore,
        authApi: AuthApi,
    ): OkHttpClient =
        plain
            .newBuilder()
            .addInterceptor(CompressionInterceptor())
            .addInterceptor(AuthInterceptor(store, authApi))
            .build()

    @Provides @Singleton
    @Named("sse")
    fun sseHttp(
        @Named("api") api: OkHttpClient,
    ): OkHttpClient = api.newBuilder().readTimeout(SSE_READ_TIMEOUT_S, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun execClient(
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
    ) = ExecClient(api, { store.credentials()?.serverUrl }, Dispatchers.IO)

    @Provides @Singleton
    fun syncSummarySink() = SyncSummarySink()

    @Provides @Singleton
    fun gomuksConnection(
        @Named("sse") sse: OkHttpClient,
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
        sink: SyncSummarySink,
    ): GomuksConnection {
        val server = { store.credentials()?.serverUrl }
        return GomuksConnection(
            SseClient(sse, server, Dispatchers.IO),
            api,
            server,
            InMemoryResumeStore(),
            Dispatchers.IO
        ) {
            sink.accept(it)
        }
    }

    @Provides @Singleton
    fun foregroundConnection(
        @ApplicationContext context: Context,
        connection: GomuksConnection,
        store: CredentialStore,
        scope: CoroutineScope,
    ) = ForegroundConnection(context, connection, store.loggedIn, scope)

    @Provides @Singleton
    fun sessionRepository(
        store: CredentialStore,
        authApi: AuthApi,
    ) = SessionRepository(store, authApi, Dispatchers.IO)
}
