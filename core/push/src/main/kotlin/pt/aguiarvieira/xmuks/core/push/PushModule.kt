package pt.aguiarvieira.xmuks.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import coil3.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomShortcuts
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.notify.Actions
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PushModule {
    @Provides @Singleton
    fun pushTokens(
        registrar: Provider<PushRegistrar>,
        scope: CoroutineScope,
    ): PushTokenSource = FirebaseTokens(registrar) { work -> scope.launch { runCatching { work() } } }

    @Provides @Singleton
    fun roomShortcuts(
        @ApplicationContext context: Context,
        images: ImageLoader,
        media: MediaUrls,
    ): RoomShortcuts = PinnedShortcuts(context, images, media)

    @Provides @Singleton
    fun roomNotifier(
        @ApplicationContext context: Context,
        images: ImageLoader,
        store: CredentialStore,
        rooms: RoomListRepository,
        exec: ExecClient,
        pushRules: RoomPushRules,
    ) = RoomNotifier(
        context,
        images,
        Actions(ActionReceiver::class.java),
        // The room's `matrix:roomid/…` link, which the app opens (the same way as any Matrix link).
        roomIntent = { roomId ->
            Intent(Intent.ACTION_VIEW, Uri.parse(LinkResolver.roomUri(roomId))).setPackage(context.packageName)
        },
        server = { store.credentials()?.serverUrl },
        isDirect = { roomId -> rooms.room(roomId).first()?.isDirect },
        isMuted = { roomId -> pushRules.setting(roomId).first() in MUTED },
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
}

/** Room settings under which mentions, @room and keywords stay silent. */
private val MUTED = setOf(RoomNotifications.MentionsAndKeywords, RoomNotifications.Off)
