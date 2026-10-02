package pt.aguiarvieira.xmuks.wear

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.KeystoreSecretCipher
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.CompressionInterceptor
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.MediaCacheStrategy
import pt.aguiarvieira.xmuks.core.notify.MxcCacheKeys
import pt.aguiarvieira.xmuks.core.notify.PushPayload
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

/** The watch's whole graph: a login, a pusher registration, gomuks' exec API and the notifier. */
@Module
@InstallIn(SingletonComponent::class)
object WearModule {
    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S = 30L

    /**
     * How long a message waits for a dismissal (read on another client) before it shows. The
     * dismissal came 185 ms behind its message when measured; 2 s leaves room for a slow network.
     */
    const val HOLD_MS = 2_000L

    private const val IMAGE_CACHE_BYTES = 32L * 1024 * 1024

    @Provides @Singleton
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    fun credentialStore(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ): CredentialStore =
        CredentialStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") },
            KeystoreSecretCipher(),
            scope,
        ).also { runBlocking(Dispatchers.IO) { it.load() } }

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
    fun execClient(
        @Named("api") api: OkHttpClient,
        store: CredentialStore,
    ) = ExecClient(api, { store.credentials()?.serverUrl }, Dispatchers.IO)

    @Provides @Singleton
    @Named("watch")
    fun watchSettings(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("watch") }

    @Provides @Singleton
    fun pushRegistrar(
        @ApplicationContext context: Context,
        exec: ExecClient,
        registrar: Provider<PushRegistrar>,
        scope: CoroutineScope,
    ): PushRegistrar =
        PushRegistrar(
            exec,
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("push") },
            KeystoreSecretCipher("xmuks-push"),
            PushTokenSource {
                // As on the phone (FirebaseTokens): register() alone never hands over the token.
                runCatching {
                    val messaging = FirebaseMessaging.getInstance()
                    messaging.register()
                    @Suppress("DEPRECATION")
                    messaging.token.addOnSuccessListener { token ->
                        scope.launch { runCatching { registrar.get().tokenReceived(token) } }
                    }
                }
            },
        )

    /**
     * Notification avatars and pictures, through gomuks' client, kept on the watch: cached by
     * mxc:// (every push's URL carries a new media token, so by URL nothing would ever be reused)
     * and trusted for a month, as on the phone. A repeat sender's avatar costs no 4G at all.
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
                add(MxcCacheKeys())
                add(OkHttpNetworkFetcherFactory(callFactory = { api }, cacheStrategy = { MediaCacheStrategy() }))
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }.build()

    @Provides @Singleton
    fun roomNotifier(
        @ApplicationContext context: Context,
        images: ImageLoader,
        store: CredentialStore,
        exec: ExecClient,
    ) = RoomNotifier(
        context,
        images,
        Actions(WatchActionReceiver::class.java),
        server = { store.credentials()?.serverUrl },
        // Only gomuks' own word (is_dm) and the room's name tell a DM here: the watch keeps no rooms.
        isDirect = { null },
        eventOf = { roomId, eventId ->
            val params =
                buildJsonObject {
                    put("room_id", JsonPrimitive(roomId))
                    put("event_id", JsonPrimitive(eventId))
                }
            (exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok)?.let {
                runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), it.data) }.getOrNull()
            }
        },
    )

    @Provides @Singleton
    fun holdback(
        scope: CoroutineScope,
        notifier: RoomNotifier,
    ) = Holdback(
        scope,
        HOLD_MS,
        show = { messages, imageAuth ->
            Log.d("xmuks-wear", "Showing ${messages.size} message(s) after the hold")
            notifier.show(PushPayload(messages = messages, imageAuth = imageAuth), null)
        },
        dismiss = notifier::dismissed,
    )
}
