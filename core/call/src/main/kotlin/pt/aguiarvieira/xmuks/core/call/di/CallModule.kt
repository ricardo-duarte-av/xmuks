package pt.aguiarvieira.xmuks.core.call.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.media.SfuTokens
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CallModule {
    @Provides @Singleton
    fun rtcApi(exec: ExecClient) = RtcApi(exec)

    /** The JWT service is not gomuks: plain HTTP, no gomuks auth. */
    @Provides @Singleton
    fun sfuTokens(
        api: RtcApi,
        @Named("plain") http: OkHttpClient,
    ) = SfuTokens(api, http, Dispatchers.IO)

    @Provides @Singleton
    fun callManager(
        @ApplicationContext context: Context,
        api: RtcApi,
        tokens: SfuTokens,
        frames: StreamFrames,
        database: XmuksDatabase,
        scope: CoroutineScope,
    ) = CallManager(context, api, tokens, frames, database, scope)
}
