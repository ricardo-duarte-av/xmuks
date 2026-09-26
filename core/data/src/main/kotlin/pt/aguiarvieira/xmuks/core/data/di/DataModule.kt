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
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
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

    @Provides @Singleton
    fun database(
        @ApplicationContext context: Context,
    ) = XmuksDatabase.build(context)

    @Provides @Singleton
    fun syncIngestor(database: XmuksDatabase) = SyncIngestor(database)

    @Provides @Singleton
    fun streamStats() = StreamStatsTracker()

    /** Everything wiped when the account changes (logout, or login to a different account). */
    @Provides @Singleton
    fun accountScoped(
        ingestor: SyncIngestor,
        stats: StreamStatsTracker,
    ): Set<AccountScoped> = setOf(ingestor, stats)

    @Provides @Singleton
    fun gomuksConnection(
        @Named("sse") sse: OkHttpClient,
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
        ingestor: SyncIngestor,
        stats: StreamStatsTracker,
    ): GomuksConnection {
        val server = { store.credentials()?.serverUrl }
        return GomuksConnection(
            SseClient(sse, server, Dispatchers.IO),
            api,
            server,
            ingestor,
            Dispatchers.IO,
        ) { frame ->
            ingestor.apply(frame)
            stats.accept(frame)
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
        accountScoped: Set<@JvmSuppressWildcards AccountScoped>,
    ) = SessionRepository(store, authApi, Dispatchers.IO, accountScoped)
}
