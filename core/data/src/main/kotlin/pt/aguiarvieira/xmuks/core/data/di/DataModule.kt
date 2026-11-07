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
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.AccountStarter
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.data.account.AppDependencies
import pt.aguiarvieira.xmuks.core.data.account.RoomShortcutsFactory
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.LiveTasks
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.contacts.ContactLinks
import pt.aguiarvieira.xmuks.core.data.contacts.ContactsSync
import pt.aguiarvieira.xmuks.core.data.contacts.PhoneContacts
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.LinkPreviewFetcher
import pt.aguiarvieira.xmuks.core.data.media.MediaDownloads
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaShares
import pt.aguiarvieira.xmuks.core.data.media.MediaUploader
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.media.PdfFiles
import pt.aguiarvieira.xmuks.core.data.media.VideoFrames
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfiles
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.data.push.PushRuleEditor
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository
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
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceEditRepository
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.data.timeline.CodeThemes
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore
import pt.aguiarvieira.xmuks.core.data.timeline.EventCache
import pt.aguiarvieira.xmuks.core.data.timeline.MediaForwarder
import pt.aguiarvieira.xmuks.core.data.timeline.PinBarStore
import pt.aguiarvieira.xmuks.core.data.timeline.RoomGallery
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSessions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineStore
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.notify.MediaCacheStrategy
import pt.aguiarvieira.xmuks.core.notify.MxcCacheKeys
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
    fun accountRegistry(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ): AccountRegistry {
        val registry =
            AccountRegistry(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("accounts") },
                sessionStore = { id ->
                    PreferenceDataStoreFactory.create {
                        context.preferencesDataStoreFile(AccountRegistry.fileName(id, "session"))
                    }
                },
                KeystoreSecretCipher(),
                scope,
            )
        // Small file reads, before anything can issue a request: network code reads logins synchronously.
        return registry.also { runBlocking(Dispatchers.IO) { it.load() } }
    }

    @Provides @Singleton
    fun accounts(
        registry: AccountRegistry,
        app: AppDependencies,
        starters: Set<@JvmSuppressWildcards AccountStarter>,
        scope: CoroutineScope,
    ) = Accounts(registry, app, starters, scope)

    @Provides @Singleton
    fun appDependencies(
        @ApplicationContext context: Context,
        @Named("plain") plain: OkHttpClient,
        @Named("api") api: OkHttpClient,
        @Named("sse") sse: OkHttpClient,
        @Named("media") media: OkHttpClient,
        images: ImageLoader,
        pdf: PdfFiles,
        phoneContacts: PhoneContacts,
        tokens: PushTokenSource,
        shortcuts: RoomShortcutsFactory,
    ) = AppDependencies(context, plain, api, sse, media, images, pdf, phoneContacts, tokens, shortcuts::create)

    /** What runs for every account from the moment its graph is built. */
    @Provides @IntoSet
    fun dataStarter(): AccountStarter =
        AccountStarter { graph ->
            // The stream follows the app's lifecycle, which is watched from the main thread.
            graph.scope.launch(Dispatchers.Main) { graph.foregroundConnection.install() }
            // DM partners in the phone's Contacts, when that's turned on.
            graph.contactsSync.start()
            // Messages left unsent by a previous run go out as soon as the app is up.
            graph.outbox.start()
        }

    /** The account on screen: what everything below is, for it. */
    @Provides
    fun accountGraph(accounts: Accounts): AccountGraph = accounts.current()

    @Provides
    fun credentialStore(graph: AccountGraph): CredentialStore = graph.store

    @Provides
    fun execClient(graph: AccountGraph): ExecClient = graph.exec

    @Provides
    fun database(graph: AccountGraph): XmuksDatabase = graph.database

    @Provides
    fun syncIngestor(graph: AccountGraph): SyncIngestor = graph.syncIngestor

    @Provides
    fun streamStats(graph: AccountGraph): StreamStatsTracker = graph.streamStats

    @Provides
    fun streamFrames(graph: AccountGraph): StreamFrames = graph.streamFrames

    @Provides @Singleton
    fun contactLinks(
        @ApplicationContext context: Context,
    ) = ContactLinks(context)

    @Provides @Singleton
    fun phoneContacts(
        @ApplicationContext context: Context,
    ) = PhoneContacts(context)

    @Provides
    fun contactsSync(graph: AccountGraph): ContactsSync = graph.contactsSync

    @Provides
    fun roomCalls(graph: AccountGraph): RoomCalls = graph.roomCalls

    @Provides
    fun eventCache(graph: AccountGraph): EventCache = graph.eventCache

    @Provides
    fun preferenceStore(graph: AccountGraph): PreferenceStore = graph.preferences

    @Provides
    fun pushRegistrar(graph: AccountGraph): PushRegistrar = graph.pushRegistrar

    @Provides @Singleton
    fun mediaPreparer(
        @ApplicationContext context: Context,
    ) = MediaPreparer(context, Dispatchers.IO)

    @Provides
    fun mediaSender(graph: AccountGraph): MediaSender = graph.mediaSender

    @Provides
    fun mediaForwarder(graph: AccountGraph): MediaForwarder = graph.mediaForwarder

    @Provides
    fun draftStore(graph: AccountGraph): DraftStore = graph.drafts

    @Provides
    fun pinBarStore(graph: AccountGraph): PinBarStore = graph.pinBars

    @Provides
    fun timelineStore(graph: AccountGraph): TimelineStore = graph.timelines

    @Provides
    fun gomuksConnection(graph: AccountGraph): GomuksConnection = graph.connection

    @Provides
    fun foregroundConnection(graph: AccountGraph): ForegroundConnection = graph.foregroundConnection

    @Provides
    fun mediaUrls(graph: AccountGraph): MediaUrls = graph.mediaUrls

    @Provides
    fun roomListRepository(graph: AccountGraph): RoomListRepository = graph.roomListRepository

    @Provides
    fun mediaDownloads(graph: AccountGraph): MediaDownloads = graph.mediaDownloads

    @Provides
    fun mediaShares(graph: AccountGraph): MediaShares = graph.mediaShares

    @Provides
    fun mediaUploader(graph: AccountGraph): MediaUploader = graph.mediaUploader

    @Provides
    fun linkPreviews(graph: AccountGraph): LinkPreviewFetcher = graph.linkPreviews

    @Provides
    fun codeThemes(graph: AccountGraph): CodeThemes = graph.codeThemes

    @Provides
    fun profileRepository(graph: AccountGraph): ProfileRepository = graph.profiles

    @Provides
    fun openRoom(graph: AccountGraph): OpenRoom = graph.openRoom

    @Provides
    fun roomPushRules(graph: AccountGraph): RoomPushRules = graph.roomPushRules

    @Provides
    fun pushRuleEditor(graph: AccountGraph): PushRuleEditor = graph.pushRuleEditor

    @Provides
    fun bridgeScanner(graph: AccountGraph): BridgeScanner = graph.bridgeScanner

    @Provides
    fun roomGallery(graph: AccountGraph): RoomGallery = graph.roomGallery

    @Provides
    fun roomShortcuts(graph: AccountGraph): RoomShortcuts = graph.shortcuts

    @Provides
    fun roomListActions(graph: AccountGraph): RoomListActions = graph.roomListActions

    @Provides
    fun mentionTargets(graph: AccountGraph): MentionTargets = graph.mentionTargets

    @Provides
    fun roomProfiles(graph: AccountGraph): RoomProfiles = graph.roomProfiles

    @Provides
    fun foundEvents(graph: AccountGraph): FoundEvents = graph.foundEvents

    @Provides
    fun mentions(graph: AccountGraph): Mentions = graph.mentions

    @Provides
    fun messageSearch(graph: AccountGraph): MessageSearch = graph.messageSearch

    @Provides
    fun contacts(graph: AccountGraph): Contacts = graph.contacts

    @Provides
    fun invitesRepository(graph: AccountGraph): InvitesRepository = graph.invites

    @Provides
    fun roomInfoRepository(graph: AccountGraph): RoomInfoRepository = graph.roomInfo

    @Provides
    fun spaceEditRepository(graph: AccountGraph): SpaceEditRepository = graph.spaceEdits

    @Provides
    fun roomDirectoryRepository(graph: AccountGraph): RoomDirectoryRepository = graph.roomDirectory

    @Provides
    fun linkResolver(graph: AccountGraph): LinkResolver = graph.linkResolver

    @Provides
    fun syncController(graph: AccountGraph): SyncController = graph.syncController

    @Provides
    fun liveTasks(graph: AccountGraph): LiveTasks = graph.liveTasks

    @Provides
    fun roomSessions(graph: AccountGraph): RoomSessions = graph.roomSessions

    @Provides
    fun outboxDatabase(graph: AccountGraph): OutboxDatabase = graph.outboxDatabase

    @Provides
    fun outbox(graph: AccountGraph): Outbox = graph.outbox

    @Provides @Singleton
    fun sessionRepository(
        accounts: Accounts,
        authApi: AuthApi,
        scope: CoroutineScope,
    ) = SessionRepository(accounts.registry, authApi, Dispatchers.IO, scope) { id -> accounts.graph(id).accountScoped }

    /**
     * Coil loads media through the same authenticated client as the API (session cookie, token
     * refresh). Two tiers, each with its own disk cache so neither pushes the other out: this one
     * (the app's default) for avatars, emoji and stickers — small, seen everywhere, kept long —
     * and [mediaImageLoader] for timeline pictures and the viewer. Shared by every account: an
     * mxc:// is the same file whichever account sees it, and each request carries the login of
     * the account whose gomuks it goes to.
     */
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
        pdf: PdfFiles,
    ): ImageLoader = buildImageLoader(context, api, pdf, "images", SMALL_IMAGES_BYTES)

    /** Timeline pictures, thumbnails and the viewer's full images: bigger, and churning faster. */
    @Provides
    @Singleton
    @Named("media")
    fun mediaImageLoader(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
        pdf: PdfFiles,
    ): ImageLoader = buildImageLoader(context, api, pdf, "media-images", MEDIA_IMAGES_BYTES)

    /** PDFs, kept whole for their pages to be drawn (the timeline's preview, the viewer). */
    @Provides
    @Singleton
    fun pdfFiles(
        @ApplicationContext context: Context,
        @Named("api") api: OkHttpClient,
    ) = PdfFiles(api, context.cacheDir.resolve("pdf"), Dispatchers.IO)

    @OptIn(ExperimentalCoilApi::class)
    private fun buildImageLoader(
        context: Context,
        api: OkHttpClient,
        pdf: PdfFiles,
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
                // A frame for videos sent without a thumbnail (before the network fetcher: its URLs).
                add(VideoFrames(api, context.cacheDir.resolve("video-frames")))
                // PDFs' pages, from their URLs with #pdf or #page=N.
                add(pdf.fetcher())
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
}
