package pt.aguiarvieira.xmuks.core.call.di

import android.content.Context
import coil3.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.call.AccountCalls
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
    @Provides @Singleton
    fun accountCalls(
        @ApplicationContext context: Context,
        images: ImageLoader,
        @Named("plain") http: OkHttpClient,
        telecom: TelecomCall,
    ) = AccountCalls(context, images, http, telecom)

    @Provides
    fun rtcApi(
        calls: AccountCalls,
        graph: AccountGraph,
    ): RtcApi = calls.rtcApi(graph)

    @Provides
    fun sfuTokens(
        calls: AccountCalls,
        graph: AccountGraph,
    ): SfuTokens = calls.sfuTokens(graph)

    @Provides
    fun callManager(
        calls: AccountCalls,
        graph: AccountGraph,
    ): CallManager = calls.manager(graph)

    @Provides @Singleton
    fun callAudio() = CallAudio()

    @Provides
    fun ringer(
        calls: AccountCalls,
        graph: AccountGraph,
    ): Ringer = calls.ringer(graph)

    @Provides
    fun incomingCalls(
        calls: AccountCalls,
        graph: AccountGraph,
    ): IncomingCalls = calls.incoming(graph)

    /** Rings arriving over an account's stream ring from the moment its graph is built. */
    @Provides @IntoSet
    fun callStarter(calls: AccountCalls): AccountStarter = AccountStarter { graph -> calls.incoming(graph) }

    @Provides @Singleton
    fun telecomCall(
        @ApplicationContext context: Context,
        audio: CallAudio,
    ) = TelecomCall(context, audio)
}
