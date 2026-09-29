package pt.aguiarvieira.xmuks.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineStore
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.SseClient
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // one provider per binding: splitting would only scatter the graph
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
        timelines: TimelineStore,
        outbox: Outbox,
    ): Set<AccountScoped> = setOf(ingestor, stats, timelines, outbox)

    /** Session-only timelines, paged from gomuks with `paginate` and kept live by the stream. */
    @Provides @Singleton
    fun timelineStore(
        exec: ExecClient,
        scope: CoroutineScope,
    ) = TimelineStore(
        paginator = { roomId, maxTimelineId, limit ->
            val params =
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("max_timeline_id", JsonPrimitive(maxTimelineId))
                    put("limit", JsonPrimitive(limit))
                }
            (exec.exec("paginate", params, ExecMode.Read) as? ExecResult.Ok)?.let {
                runCatching { GomuksJson.decodeFromJsonElement(PaginationResponse.serializer(), it.data) }.getOrNull()
            }
        },
        scope = scope,
    )

    @Provides @Singleton
    fun gomuksConnection(
        @Named("sse") sse: OkHttpClient,
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
        ingestor: SyncIngestor,
        stats: StreamStatsTracker,
        liveTasks: LiveTasks,
        timelines: TimelineStore,
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
            timelines.onFrame(frame)
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
    fun profileRepository(
        exec: ExecClient,
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
        database: XmuksDatabase,
        ingestor: SyncIngestor,
    ) = ProfileRepository(exec, api, { store.credentials()?.serverUrl }, database, ingestor, Dispatchers.IO)

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
                // Animated GIF / WebP / HEIF (custom emoji, stickers, images) play, not just their
                // first frame. The platform decoder: our minSdk (31) always has it.
                add(AnimatedImageDecoder.Factory())
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
    fun roomSessions(
        timelines: TimelineStore,
        exec: ExecClient,
        database: XmuksDatabase,
        outbox: Outbox,
        scope: CoroutineScope,
    ) = RoomSessions(timelines, exec, database, outbox, scope)

    /** Unsent messages: their own database, which (unlike the cache) survives schema changes. */
    @Provides @Singleton
    fun outboxDatabase(
        @ApplicationContext context: Context,
    ) = OutboxDatabase.build(context)

    @Provides @Singleton
    fun outbox(
        database: OutboxDatabase,
        exec: ExecClient,
        timelines: TimelineStore,
        scope: CoroutineScope,
    ) = Outbox(database.outboxDao(), exec::execOnce, timelines::addLocalEcho, scope)

    @Provides @Singleton
    fun sessionRepository(
        store: CredentialStore,
        authApi: AuthApi,
        accountScoped: Set<@JvmSuppressWildcards AccountScoped>,
    ) = SessionRepository(store, authApi, Dispatchers.IO, accountScoped)
}
