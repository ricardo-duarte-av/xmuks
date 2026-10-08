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
import coil3.svg.SvgDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.LiveTasks
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.LinkPreviewFetcher
import pt.aguiarvieira.xmuks.core.data.media.MediaDownloads
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaUploader
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfiles
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.rooms.BridgeScanner
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvents
import pt.aguiarvieira.xmuks.core.data.rooms.InvitesRepository
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTargets
import pt.aguiarvieira.xmuks.core.data.rooms.Mentions
import pt.aguiarvieira.xmuks.core.data.rooms.MessageSearch
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListActions
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomShortcuts
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore
import pt.aguiarvieira.xmuks.core.data.timeline.RoomGallery
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineStore
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.SseClient
import pt.aguiarvieira.xmuks.core.notify.MediaCacheStrategy
import pt.aguiarvieira.xmuks.core.notify.MxcCacheKeys
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // one provider per binding: splitting would only scatter the graph
object DataModule {
    private const val SMALL_IMAGES_BYTES = 128L * 1024 * 1024
    private const val MEDIA_IMAGES_BYTES = 512L * 1024 * 1024

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
        drafts: DraftStore,
        uploads: MediaSender,
        push: PushRegistrar,
        preferences: PreferenceStore,
    ): Set<AccountScoped> = setOf(ingestor, stats, timelines, outbox, drafts, uploads, push, preferences)

    @Provides @Singleton
    fun preferenceStore(
        @ApplicationContext context: Context,
        exec: ExecClient,
        database: XmuksDatabase,
    ) = PreferenceStore(
        exec,
        database,
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("gomuks-preferences") },
    )

    @Provides @Singleton
    fun pushRegistrar(
        @ApplicationContext context: Context,
        exec: ExecClient,
        tokens: PushTokenSource,
    ) = PushRegistrar(
        exec,
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("push") },
        KeystoreSecretCipher("xmuks-push"),
        tokens,
    )

    @Provides @Singleton
    fun mediaPreparer(
        @ApplicationContext context: Context,
    ) = MediaPreparer(context, Dispatchers.IO)

    @Provides @Singleton
    fun mediaSender(
        uploader: MediaUploader,
        outbox: Outbox,
        scope: CoroutineScope,
    ) = MediaSender(uploader, outbox, scope)

    @Provides @Singleton
    fun draftStore(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ) = DraftStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("drafts") }, scope)

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
        scope: CoroutineScope,
        bridges: BridgeScanner,
    ): GomuksConnection {
        val server = { store.credentials()?.serverUrl }
        val connection =
            GomuksConnection(
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
        // Timelines are only as current as the stream: they learn when it drops (see TimelineStore).
        scope.launch {
            connection.state
                .map { it == ConnectionState.Live }
                .distinctUntilChanged()
                .collect {
                    timelines.streamChanged(it)
                    bridges.streamChanged(it)
                }
        }
        return connection
    }

    @Provides @Singleton
    fun foregroundConnection(
        @ApplicationContext context: Context,
        connection: GomuksConnection,
        store: CredentialStore,
        scope: CoroutineScope,
        timelines: TimelineStore,
        @Named("plain") http: OkHttpClient,
    ) = ForegroundConnection(
        context,
        connection,
        store.loggedIn,
        scope,
        // Back in the foreground: a room still on screen from before may have missed things.
        onForeground = { scope.launch { timelines.refreshWatched() } },
        // Every client is built from this one, so they share its pool (images included).
        dropIdleConnections = { http.connectionPool.evictAll() },
    )

    @Provides @Singleton
    fun mediaUrls(store: CredentialStore) = MediaUrls { store.credentials()?.serverUrl }

    @Provides @Singleton
    fun roomListRepository(
        database: XmuksDatabase,
        media: MediaUrls,
    ) = RoomListRepository(database, media)

    @Provides @Singleton
    fun mediaDownloads(
        @ApplicationContext context: Context,
        @Named("media") http: OkHttpClient,
        media: MediaUrls,
        scope: CoroutineScope,
    ) = MediaDownloads(http, media, context.contentResolver, scope, Dispatchers.IO)

    @Provides @Singleton
    fun mediaUploader(
        @Named("media") http: OkHttpClient,
        store: CredentialStore,
    ) = MediaUploader(http, { store.credentials()?.serverUrl }, Dispatchers.IO)

    @Provides @Singleton
    fun linkPreviews(
        @Named("media") http: OkHttpClient,
        store: CredentialStore,
    ) = LinkPreviewFetcher(http, { store.credentials()?.serverUrl }, Dispatchers.IO)

    @Provides @Singleton
    fun profileRepository(
        exec: ExecClient,
        uploader: MediaUploader,
        database: XmuksDatabase,
        ingestor: SyncIngestor,
    ) = ProfileRepository(exec, uploader, database, ingestor)

    @Provides @Singleton
    fun openRoom() = OpenRoom()

    @Provides @Singleton
    fun roomPushRules(
        database: XmuksDatabase,
        exec: ExecClient,
    ) = RoomPushRules(database, exec)

    @Provides @Singleton
    fun bridgeScanner(
        exec: ExecClient,
        database: XmuksDatabase,
        scope: CoroutineScope,
    ) = BridgeScanner(exec, database, scope)

    @Provides @Singleton
    fun roomGallery(
        exec: ExecClient,
        database: XmuksDatabase,
    ) = RoomGallery(exec, database)

    @Provides @Singleton
    fun roomListActions(
        exec: ExecClient,
        database: XmuksDatabase,
        pushRules: RoomPushRules,
        preferences: PreferenceStore,
        shortcuts: RoomShortcuts,
    ) = RoomListActions(exec, database, pushRules, preferences, shortcuts)

    @Provides @Singleton
    fun mentionTargets(
        roomInfo: RoomInfoRepository,
        database: XmuksDatabase,
    ) = MentionTargets(roomInfo, database)

    @Provides @Singleton
    fun roomProfiles(
        database: XmuksDatabase,
        rooms: RoomListRepository,
    ) = RoomProfiles(database, rooms)

    @Provides @Singleton
    fun foundEvents(
        database: XmuksDatabase,
        rooms: RoomListRepository,
    ) = FoundEvents(database, rooms)

    @Provides @Singleton
    fun mentions(
        exec: ExecClient,
        found: FoundEvents,
    ) = Mentions(exec, found)

    @Provides @Singleton
    fun messageSearch(
        exec: ExecClient,
        found: FoundEvents,
    ) = MessageSearch(exec, found)

    @Provides @Singleton
    fun contacts(
        exec: ExecClient,
        database: XmuksDatabase,
        rooms: RoomListRepository,
    ) = Contacts(exec, database, rooms)

    @Provides @Singleton
    fun invitesRepository(
        database: XmuksDatabase,
        rooms: RoomInfoRepository,
        contacts: Contacts,
        media: MediaUrls,
    ) = InvitesRepository(database.roomListDao(), rooms, contacts, media)

    @Provides @Singleton
    fun roomInfoRepository(
        exec: ExecClient,
        database: XmuksDatabase,
        ingestor: SyncIngestor,
    ) = RoomInfoRepository(exec, database, ingestor)

    @Provides @Singleton
    fun linkResolver(
        exec: ExecClient,
        rooms: RoomListRepository,
    ) = LinkResolver(exec, rooms)

    @Provides @Singleton
    fun syncController(
        ingestor: SyncIngestor,
        connection: ForegroundConnection,
    ) = SyncController(ingestor, connection)

    /**
     * Coil loads media through the same authenticated client as the API (session cookie, token
     * refresh). Two tiers, each with its own disk cache so neither pushes the other out: this one
     * (the app's default) for avatars, emoji and stickers — small, seen everywhere, kept long —
     * and [mediaImageLoader] for timeline pictures and the viewer.
     */
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
    ): ImageLoader = buildImageLoader(context, api, "images", SMALL_IMAGES_BYTES)

    /** Timeline pictures, thumbnails and the viewer's full images: bigger, and churning faster. */
    @Provides
    @Singleton
    @Named("media")
    fun mediaImageLoader(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
    ): ImageLoader = buildImageLoader(context, api, "media-images", MEDIA_IMAGES_BYTES)

    @OptIn(ExperimentalCoilApi::class)
    private fun buildImageLoader(
        context: Context,
        api: OkHttpClient,
        directory: String,
        maxBytes: Long,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Cached by mxc:// (and thumbnail size), whatever URL fetched it.
                add(MxcCacheKeys())
                // Animated GIF / WebP / HEIF (custom emoji, stickers, images) play, not just their
                // first frame. The platform decoder: our minSdk (31) always has it.
                add(AnimatedImageDecoder.Factory())
                // Bio banners and the odd custom emoji are SVG.
                add(SvgDecoder.Factory())
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
                    .directory(context.cacheDir.resolve(directory).toOkioPath())
                    .maxSizeBytes(maxBytes)
                    .build()
            }.build()

    @Provides @Singleton
    fun liveTasks(
        @ApplicationContext context: Context,
        push: PushRegistrar,
        exec: ExecClient,
        ingestor: SyncIngestor,
        database: XmuksDatabase,
        media: MediaUrls,
        imageLoader: ImageLoader,
        scope: CoroutineScope,
    ) = LiveTasks(context, exec, ingestor, database, media, imageLoader, scope, push)

    @Provides @Singleton
    fun roomSessions(
        timelines: TimelineStore,
        exec: ExecClient,
        database: XmuksDatabase,
        outbox: Outbox,
        uploads: MediaSender,
        scope: CoroutineScope,
        prefs: PreferenceStore,
        ingestor: SyncIngestor,
    ) = RoomSessions(timelines, exec, database, outbox, uploads, scope, prefs, ingestor.stateChanged)

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
