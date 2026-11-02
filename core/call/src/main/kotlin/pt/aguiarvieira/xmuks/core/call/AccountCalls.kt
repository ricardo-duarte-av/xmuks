package pt.aguiarvieira.xmuks.core.call

import android.content.Context
import coil3.ImageLoader
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import pt.aguiarvieira.xmuks.core.call.media.SfuTokens
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.call.system.Ringer
import pt.aguiarvieira.xmuks.core.call.system.TelecomCall
import pt.aguiarvieira.xmuks.core.data.account.AccountGraph

/**
 * Each account's calls, kept in its graph: its ringing, its call manager. A ring or a call stays
 * its account's whichever account is shown (answering one shows its account).
 */
class AccountCalls(
    private val context: Context,
    private val images: ImageLoader,
    /** For the JWT service, which is not gomuks: plain HTTP, no gomuks auth. */
    private val plainHttp: OkHttpClient,
    private val telecom: TelecomCall,
) {
    fun rtcApi(graph: AccountGraph): RtcApi = graph.scoped(RtcApi::class) { RtcApi(graph.exec) }

    fun sfuTokens(graph: AccountGraph): SfuTokens =
        graph.scoped(SfuTokens::class) { SfuTokens(rtcApi(graph), plainHttp, Dispatchers.IO) }

    fun manager(graph: AccountGraph): CallManager =
        graph.scoped(CallManager::class) {
            CallManager(
                context,
                rtcApi(graph),
                sfuTokens(graph),
                graph.streamFrames,
                graph.database,
                graph.roomCalls,
                telecom,
                graph.preferences,
                graph.scope,
                account = graph.id,
            )
        }

    fun ringer(graph: AccountGraph): Ringer =
        graph.scoped(Ringer::class) {
            Ringer(context, images, graph.roomListRepository, graph.scope, account = graph.id)
        }

    fun incoming(graph: AccountGraph): IncomingCalls =
        graph.scoped(IncomingCalls::class) {
            IncomingCalls(
                rtcApi(graph),
                graph.roomCalls,
                manager(graph),
                graph.database,
                ringer(graph),
                graph.streamFrames,
                graph.scope,
            )
        }
}
