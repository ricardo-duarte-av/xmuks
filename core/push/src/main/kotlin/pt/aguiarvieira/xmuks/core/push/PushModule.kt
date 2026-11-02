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
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.AccountStarter
import pt.aguiarvieira.xmuks.core.data.account.RoomShortcutsFactory
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
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

    /** Each account's shortcuts: kept in its graph. */
    @Provides @Singleton
    fun roomShortcutsFactory(
        @ApplicationContext context: Context,
        images: ImageLoader,
    ) = RoomShortcutsFactory { graph ->
        graph.scoped(PinnedShortcuts::class) { PinnedShortcuts(context, images, graph.mediaUrls) }
    }

    @Provides
    fun pinnedShortcuts(graph: AccountGraph): PinnedShortcuts = graph.shortcuts as PinnedShortcuts

    @Provides
    fun roomNotifier(
        @ApplicationContext context: Context,
        images: ImageLoader,
        graph: AccountGraph,
    ): RoomNotifier = notifier(context, images, graph)

    /** What runs for every account from the moment its graph is built. */
    @Provides @IntoSet
    fun pushStarter(
        @ApplicationContext context: Context,
        images: ImageLoader,
    ): AccountStarter =
        AccountStarter { graph ->
            // Home-screen and conversation shortcuts follow their room's name and avatar.
            (graph.shortcuts as PinnedShortcuts).follow(graph.roomListRepository.chats(), graph.scope)
            // Opening a room is reading it: its notification goes.
            graph.openRoom.onOpened = notifier(context, images, graph)::clear
        }

    private fun notifier(
        context: Context,
        images: ImageLoader,
        graph: AccountGraph,
    ) = graph.scoped(RoomNotifier::class) {
        RoomNotifier(
            context,
            images,
            Actions(ActionReceiver::class.java),
            // The room's `matrix:roomid/…` link, which the app opens (the same way as any Matrix link).
            roomIntent = { roomId ->
                Intent(Intent.ACTION_VIEW, Uri.parse(LinkResolver.roomUri(roomId))).setPackage(context.packageName)
            },
            server = { graph.store.credentials()?.serverUrl },
            isDirect = { roomId ->
                graph.roomListRepository
                    .room(roomId)
                    .first()
                    ?.isDirect
            },
            isMuted = { roomId -> graph.roomPushRules.setting(roomId).first() in MUTED },
            dmSound = { graph.preferences.value(Prefs.dmSound).first() },
            eventOf = { roomId, eventId ->
                val params =
                    buildJsonObject {
                        put("room_id", JsonPrimitive(roomId))
                        put("event_id", JsonPrimitive(eventId))
                    }
                (graph.exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok)?.let {
                    runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), it.data) }.getOrNull()
                }
            },
        )
    }
}

/** Room settings under which mentions, @room and keywords stay silent. */
private val MUTED = setOf(RoomNotifications.MentionsAndKeywords, RoomNotifications.Off)
