package pt.aguiarvieira.xmuks.core.data.connection

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame

/**
 * Work that runs each time the stream goes live: refresh our own profile from the homeserver, and
 * warm the avatar cache so the room list (and, later, notifications) never waits on the network.
 *
 * A changed avatar is a new `mxc://` URI, so only new or changed avatars cost a request; see
 * [pt.aguiarvieira.xmuks.core.data.media.MediaCacheStrategy] for how long cached ones are trusted.
 */
class LiveTasks(
    private val context: Context,
    private val exec: ExecClient,
    private val ingestor: SyncIngestor,
    private val database: XmuksDatabase,
    private val media: MediaUrls,
    private val imageLoader: ImageLoader,
    private val scope: CoroutineScope,
) {
    private var running: Job? = null

    fun onFrame(frame: GomuksFrame) {
        if (frame.event != GomuksEvent.InitComplete) return
        if (running?.isActive == true) return
        running =
            scope.launch {
                refreshOwnProfile()
                prefetchAvatars()
            }
    }

    private suspend fun refreshOwnProfile() {
        val userId =
            database
                .roomListDao()
                .ownProfile()
                .first()
                ?.userId ?: return
        val result = exec.exec("get_profile", buildJsonObject { put("user_id", JsonPrimitive(userId)) }, ExecMode.Read)
        // Response shape: {"profile": {"displayname": …, "avatar_url": …, …extended fields}}.
        val profile = ((result as? ExecResult.Ok)?.data as? JsonObject)?.get("profile") as? JsonObject ?: return
        // A reply that says nothing must not erase what client_state already gave us.
        if ("displayname" !in profile && "avatar_url" !in profile) return
        ingestor.updateOwnProfile(userId, profile.string("displayname"), profile.string("avatar_url"))
    }

    private suspend fun prefetchAvatars() {
        val permits = Semaphore(PARALLEL_FETCHES)
        ingestor.allAvatars().mapNotNull(media::avatar).forEach { url ->
            permits.withPermit {
                // Disk only: a cached avatar is a cache hit and no request; a missing one is fetched once.
                imageLoader.execute(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size(PREFETCH_DECODE_PX, PREFETCH_DECODE_PX))
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
            }
        }
    }

    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val PARALLEL_FETCHES = 4

        /** Decoding is unavoidable through Coil; keep it tiny since only the disk entry matters here. */
        const val PREFETCH_DECODE_PX = 32
    }
}
