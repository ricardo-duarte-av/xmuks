package pt.aguiarvieira.xmuks.core.call.di

import android.content.Context
import coil3.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.call.media.SfuTokens
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.call.system.CallAudio
import pt.aguiarvieira.xmuks.core.call.system.Ringer
import pt.aguiarvieira.xmuks.core.call.system.TelecomCall
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph
import pt.aguiarvieira.xmuks.core.data.account.AccountStarter
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CallModule {
    @Provides
    fun rtcApi(graph: AccountGraph) = graph.scoped(RtcApi::class) { RtcApi(graph.exec) }

    /** The JWT service is not gomuks: plain HTTP, no gomuks auth. */
    @Provides
    fun sfuTokens(
        graph: AccountGraph,
        @Named("plain") http: OkHttpClient,
    ) = graph.scoped(SfuTokens::class) { SfuTokens(rtcApi(graph), http, Dispatchers.IO) }

    @Provides
    fun callManager(
        @ApplicationContext context: Context,
        graph: AccountGraph,
        @Named("plain") http: OkHttpClient,
        telecom: TelecomCall,
    ) = graph.scoped(CallManager::class) {
        CallManager(
            context,
            rtcApi(graph),
            sfuTokens(graph, http),
            graph.streamFrames,
            graph.database,
            graph.roomCalls,
            telecom,
            graph.preferences,
            graph.scope,
        )
    }

    @Provides @Singleton
    fun callAudio() = CallAudio()

    @Provides
    fun ringer(
        @ApplicationContext context: Context,
        images: ImageLoader,
        graph: AccountGraph,
    ) = graph.scoped(Ringer::class) { Ringer(context, images, graph.roomListRepository, graph.scope) }

    @Provides
    fun incomingCalls(
        @ApplicationContext context: Context,
        images: ImageLoader,
        graph: AccountGraph,
        @Named("plain") http: OkHttpClient,
        telecom: TelecomCall,
    ) = graph.scoped(IncomingCalls::class) {
        IncomingCalls(
            rtcApi(graph),
            graph.roomCalls,
            callManager(context, graph, http, telecom),
            graph.database,
            ringer(context, images, graph),
            graph.streamFrames,
            graph.scope,
        )
    }

    /** Rings arriving over an account's stream ring from the moment its graph is built. */
    @Provides @IntoSet
    fun callStarter(
        @ApplicationContext context: Context,
        images: ImageLoader,
        @Named("plain") http: OkHttpClient,
        telecom: TelecomCall,
    ): AccountStarter = AccountStarter { graph -> incomingCalls(context, images, graph, http, telecom) }

    @Provides @Singleton
    fun telecomCall(
        @ApplicationContext context: Context,
        audio: CallAudio,
    ) = TelecomCall(context, audio)
}
