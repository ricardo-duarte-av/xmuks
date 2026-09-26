package pt.aguiarvieira.xmuks.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.ConnectivityChecker
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
import okio.Path.Companion.toOkioPath
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.LiveTasks
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.media.MediaCacheStrategy
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
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
    private const val IMAGE_DISK_CACHE_BYTES = 256L * 1024 * 1024

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
        liveTasks: LiveTasks,
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
            liveTasks.onFrame(frame)
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
    fun mediaUrls(store: CredentialStore) = MediaUrls { store.credentials()?.serverUrl }

    @Provides @Singleton
    fun roomListRepository(
        database: XmuksDatabase,
        media: MediaUrls,
    ) = RoomListRepository(database, media)

    @Provides @Singleton
    fun syncController(
        ingestor: SyncIngestor,
        connection: ForegroundConnection,
    ) = SyncController(ingestor, connection)

    /**
     * Coil loads media through the same authenticated client as the API (session cookie, token
     * refresh). Disk cache sized for avatars and thumbnails; M6 adds the tiered media cache.
     */
    @OptIn(ExperimentalCoilApi::class)
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { api },
                        cacheStrategy = { MediaCacheStrategy() },
                        connectivityChecker = ::ConnectivityChecker,
                        // The same avatar in the list and the header is fetched once, not twice.
                        concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
                    ),
                )
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }.build()

    @Provides @Singleton
    fun liveTasks(
        @ApplicationContext context: Context,
        exec: ExecClient,
        ingestor: SyncIngestor,
        database: XmuksDatabase,
        media: MediaUrls,
        imageLoader: ImageLoader,
        scope: CoroutineScope,
    ) = LiveTasks(context, exec, ingestor, database, media, imageLoader, scope)

    @Provides @Singleton
    fun sessionRepository(
        store: CredentialStore,
        authApi: AuthApi,
        accountScoped: Set<@JvmSuppressWildcards AccountScoped>,
    ) = SessionRepository(store, authApi, Dispatchers.IO, accountScoped)
}
