package pt.aguiarvieira.xmuks.core.push

import android.content.Context
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
import pt.aguiarvieira.xmuks.core.account.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.AccountStarter
import pt.aguiarvieira.xmuks.core.data.account.Accounts
import pt.aguiarvieira.xmuks.core.data.account.RoomShortcutsFactory
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
        accounts: Provider<Accounts>,
        scope: CoroutineScope,
    ): PushTokenSource =
        FirebaseTokens({ accounts.get().signedIn().map { it.pushRegistrar } }) { work ->
            scope.launch { runCatching { work() } }
        }

    /** Each account's shortcuts: kept in its graph. */
    @Provides @Singleton
    fun roomShortcutsFactory(
        @ApplicationContext context: Context,
        images: ImageLoader,
    ) = RoomShortcutsFactory { graph ->
        graph.scoped(PinnedShortcuts::class) {
            PinnedShortcuts(context, images, graph.mediaUrls, graph.roomAccount, graph::roomIntent)
        }
    }

    @Provides
    fun pinnedShortcuts(graph: AccountGraph): PinnedShortcuts = graph.shortcuts as PinnedShortcuts

    @Provides @Singleton
    fun roomNotifiers(
        @ApplicationContext context: Context,
        images: ImageLoader,
    ) = RoomNotifiers(context, images)

    @Provides
    fun roomNotifier(
        notifiers: RoomNotifiers,
        graph: AccountGraph,
    ): RoomNotifier = notifiers.of(graph)

    /** What runs for every account from the moment its graph is built. */
    @Provides @IntoSet
    fun pushStarter(notifiers: RoomNotifiers): AccountStarter =
        AccountStarter { graph ->
            // Home-screen and conversation shortcuts follow their room's name and avatar.
            (graph.shortcuts as PinnedShortcuts).follow(graph.roomListRepository.chats(), graph.scope)
            // Opening a room is reading it: its notification goes.
            graph.openRoom.onOpened = notifiers.of(graph)::clear
        }
}

/** Each account's message notifications, kept in its graph. */
class RoomNotifiers(
    private val context: Context,
    private val images: ImageLoader,
) {
    fun of(graph: AccountGraph): RoomNotifier =
        graph.scoped(RoomNotifier::class) {
            RoomNotifier(
                context,
                images,
                Actions(ActionReceiver::class.java),
                // The room's `matrix:roomid/…` link, which the app opens (the same way as any Matrix link).
                roomIntent = graph::roomIntent,
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
                account = graph.roomAccount,
            )
        }
}

/** Room settings under which mentions, @room and keywords stay silent. */
private val MUTED = setOf(RoomNotifications.MentionsAndKeywords, RoomNotifications.Off)
