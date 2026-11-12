package pt.aguiarvieira.xmuks.core.data.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.LiveTasks
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.contacts.ContactsSync
import pt.aguiarvieira.xmuks.core.data.contacts.PhoneContacts
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.LinkPreviewFetcher
import pt.aguiarvieira.xmuks.core.data.media.MediaDownloads
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaShares
import pt.aguiarvieira.xmuks.core.data.media.MediaUploader
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.media.PdfFiles
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfiles
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.data.push.PushRuleEditor
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceHierarchyRepository
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
import pt.aguiarvieira.xmuks.core.data.sync.MemberStore
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
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.SseClient
import pt.aguiarvieira.xmuks.core.notify.RoomKeys
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.PaginationResponse

/** Builds an account's [RoomShortcuts] (provided where the platform shortcut code lives). */
fun interface RoomShortcutsFactory {
    fun create(graph: AccountGraph): RoomShortcuts
}

/** What every account's graph shares: the app's context, HTTP clients and image loaders. */
class AppDependencies(
    val context: Context,
    val plainHttp: OkHttpClient,
    val apiHttp: OkHttpClient,
    val sseHttp: OkHttpClient,
    val mediaHttp: OkHttpClient,
    val images: ImageLoader,
    val pdf: PdfFiles,
    val phoneContacts: PhoneContacts,
    val pushTokens: PushTokenSource,
    /** The account's home-screen and conversation shortcuts (built where the platform code lives). */
    val shortcuts: (AccountGraph) -> RoomShortcuts,
)

/**
 * Everything that belongs to one gomuks account: its login, its database and files, its stream
 * and the repositories over them. Built once per account and kept (another account being shown
 * doesn't end it: its unsent messages still go out). Parts are built when first asked for.
 *
 * Other modules keep their own per-account objects here too, with [scoped].
 */
@Suppress("TooManyFunctions") // one member per part: splitting would only scatter the graph
class AccountGraph(
    val id: String,
    val store: CredentialStore,
    /** Whether this is the account on screen: only that one streams. */
    val isActive: StateFlow<Boolean>,
    private val app: AppDependencies,
    /** Every signed-in account's Matrix user (as their caches know them). */
    private val signedInUsers: suspend () -> Set<String> = { emptySet() },
) {
    private val context = app.context

    /** Lives as long as the account's graph: everything running for it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun file(base: String) = AccountRegistry.fileName(id, base)

    /** This account in the system's names for its rooms ([RoomKeys]): null for the first account. */
    val roomAccount: String? = id.takeUnless { it == AccountRegistry.LEGACY }

    /** [roomId] as the system knows it, for this account (notification, shortcut, channel). */
    fun roomKey(roomId: String): String = RoomKeys.of(roomAccount, roomId)

    /** Opening [roomId] in the app, as this account (its `matrix:roomid/…` link). */
    fun roomIntent(roomId: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(LinkResolver.roomUri(roomId)))
            .setPackage(context.packageName)
            .putExtra(RoomKeys.EXTRA_ACCOUNT, id)

    private fun dataStore(base: String) =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(file(base)) }

    private val server = { store.credentials()?.serverUrl }

    val exec by lazy { ExecClient(app.apiHttp, server, Dispatchers.IO) }

    val database by lazy { XmuksDatabase.build(context, AccountRegistry.fileName(id, "xmuks", ".db")) }

    /** Unsent messages: their own database, which (unlike the cache) survives schema changes. */
    val outboxDatabase by lazy { OutboxDatabase.build(context, AccountRegistry.fileName(id, "outbox", ".db")) }

    val syncIngestor by lazy { SyncIngestor(database) }

    val streamStats by lazy { StreamStatsTracker() }

    val streamFrames by lazy { StreamFrames() }

    val contactsSync by lazy {
        ContactsSync(
            context,
            app.phoneContacts,
            roomListRepository,
            preferences,
            database,
            app.images,
            scope,
            signedInUsers,
        )
    }

    val roomCalls by lazy { RoomCalls(database, exec) }

    val eventCache by lazy { EventCache(exec) }

    val preferences by lazy { PreferenceStore(exec, database, dataStore("gomuks-preferences")) }

    val pushRegistrar by lazy {
        PushRegistrar(
            exec,
            dataStore("push"),
            KeystoreSecretCipher("xmuks-push"),
            app.pushTokens,
            appId = context.packageName,
        )
    }

    val mediaSender by lazy { MediaSender(mediaUploader, outbox, scope) }

    val mediaForwarder by lazy { MediaForwarder(exec, outbox) }

    val drafts by lazy { DraftStore(dataStore("drafts"), scope) }

    val pinBars by lazy { PinBarStore(dataStore("pin-bar"), scope) }

    /** Session-only timelines, paged from gomuks with `paginate` and kept live by the stream. */
    val timelines by lazy {
        TimelineStore(
            paginator = { roomId, maxTimelineId, limit ->
                val params =
                    buildJsonObject {
                        put("room_id", JsonPrimitive(roomId))
                        put("max_timeline_id", JsonPrimitive(maxTimelineId))
                        put("limit", JsonPrimitive(limit))
                    }
                (exec.exec("paginate", params, ExecMode.Read) as? ExecResult.Ok)?.let {
                    runCatching {
                        GomuksJson.decodeFromJsonElement(PaginationResponse.serializer(), it.data)
                    }.getOrNull()
                }
            },
            scope = scope,
        )
    }

    val connection by lazy {
        GomuksConnection(
            SseClient(app.sseHttp, server, Dispatchers.IO),
            app.apiHttp,
            server,
            syncIngestor,
            Dispatchers.IO,
        ) { frame ->
            syncIngestor.apply(frame)
            timelines.onFrame(frame)
            streamStats.accept(frame)
            liveTasks.onFrame(frame)
            streamFrames.accept(frame)
        }.also { connection ->
            // Timelines are only as current as the stream: they learn when it drops (see TimelineStore).
            scope.launch {
                connection.state
                    .map { it == ConnectionState.Live }
                    .distinctUntilChanged()
                    .collect {
                        timelines.streamChanged(it)
                        bridgeScanner.streamChanged(it)
                    }
            }
        }
    }

    val foregroundConnection by lazy {
        ForegroundConnection(
            context,
            connection,
            isActive,
            scope,
            // Back in the foreground: a room still on screen from before may have missed things.
            onForeground = { scope.launch { timelines.refreshWatched() } },
            // Every client is built from this one, so they share its pool (images included).
            dropIdleConnections = { app.plainHttp.connectionPool.evictAll() },
        )
    }

    val mediaUrls by lazy { MediaUrls(server) }

    val roomListRepository by lazy {
        RoomListRepository(
            database,
            mediaUrls,
            preferences.layers().map { it.get(Prefs.muteLowPriority) }.distinctUntilChanged(),
            preferences.layers().map { it.get(Prefs.dmMentions) }.distinctUntilChanged(),
        )
    }

    val mediaDownloads by lazy {
        MediaDownloads(app.mediaHttp, mediaUrls, context.contentResolver, scope, Dispatchers.IO)
    }

    /** Media handed to other apps through the share sheet. */
    val mediaShares by lazy {
        MediaShares(context, app.mediaHttp, mediaUrls, app.pdf, context.cacheDir.resolve("shares"), Dispatchers.IO)
    }

    val mediaUploader by lazy { MediaUploader(app.mediaHttp, server, Dispatchers.IO) }

    val linkPreviews by lazy { LinkPreviewFetcher(app.mediaHttp, server, Dispatchers.IO) }

    val codeThemes by lazy { CodeThemes(app.mediaHttp, server, Dispatchers.IO) }

    val profiles by lazy { ProfileRepository(exec, mediaUploader, database, syncIngestor) }

    val openRoom by lazy { OpenRoom() }

    val roomPushRules by lazy { RoomPushRules(database, exec) }

    val pushRuleEditor by lazy { PushRuleEditor(database, exec) }

    val bridgeScanner by lazy { BridgeScanner(exec, database, scope) }

    val roomGallery by lazy { RoomGallery(exec, database) }

    val shortcuts by lazy { app.shortcuts(this) }

    val roomListActions by lazy { RoomListActions(exec, database, roomPushRules, preferences, shortcuts) }

    val mentionTargets by lazy { MentionTargets(roomInfo, database) }

    val memberStore by lazy { MemberStore(database) }

    val roomProfiles by lazy { RoomProfiles(database, roomListRepository, exec, memberStore) }

    val foundEvents by lazy { FoundEvents(database, roomListRepository) }

    val mentions by lazy { Mentions(exec, foundEvents) }

    val messageSearch by lazy { MessageSearch(exec, foundEvents) }

    val contacts by lazy { Contacts(exec, database, roomListRepository) }

    val invites by lazy { InvitesRepository(database.roomListDao(), roomInfo, contacts, mediaUrls) }

    val roomInfo by lazy { RoomInfoRepository(exec, database, syncIngestor) }

    val spaceEdits by lazy { SpaceEditRepository(exec, database, roomInfo, mediaUrls) }

    val roomDirectory by lazy { RoomDirectoryRepository(exec) }

    val spaceHierarchy by lazy { SpaceHierarchyRepository(exec, database) }

    val linkResolver by lazy { LinkResolver(exec, roomListRepository) }

    val syncController by lazy { SyncController(syncIngestor, foregroundConnection) }

    val liveTasks by lazy {
        LiveTasks(context, exec, syncIngestor, database, mediaUrls, app.images, scope, pushRegistrar)
    }

    val roomSessions by lazy {
        RoomSessions(
            timelines,
            exec,
            database,
            outbox,
            mediaSender,
            scope,
            preferences,
            syncIngestor.stateChanged,
            eventCache,
            memberStore,
        )
    }

    val outbox by lazy { Outbox(outboxDatabase.outboxDao(), exec::execOnce, timelines::addLocalEcho, scope) }

    /** Everything wiped when the account signs out. */
    val accountScoped: Set<AccountScoped> by lazy {
        setOfNotNull(
            // Its home-screen shortcut, greyed out once it's signed out.
            shortcuts as? AccountScoped,
            syncIngestor,
            streamStats,
            timelines,
            outbox,
            drafts,
            pinBars,
            mediaSender,
            pushRegistrar,
            preferences,
            eventCache,
        )
    }

    private val extras = HashMap<Any, Any>()

    /** A per-account object of another module's: made with [create] the first time [key] is asked for. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> scoped(
        key: Any,
        create: () -> T,
    ): T = synchronized(extras) { extras.getOrPut(key, create) as T }
}
