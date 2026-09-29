package pt.aguiarvieira.xmuks.core.push

import android.content.Context
import coil3.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.push.PushRegistrar
import pt.aguiarvieira.xmuks.core.data.push.PushTokenSource
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
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
    fun roomNotifier(
        @ApplicationContext context: Context,
        images: ImageLoader,
        store: CredentialStore,
        rooms: RoomListRepository,
    ) = RoomNotifier(
        context,
        images,
        server = { store.credentials()?.serverUrl },
        isDirect = { roomId -> rooms.room(roomId).first()?.isDirect },
    )
}
