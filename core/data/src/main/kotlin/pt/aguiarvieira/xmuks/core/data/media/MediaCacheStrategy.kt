package pt.aguiarvieira.xmuks.core.data.media

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

/**
 * `mxc://` media is content-addressed, so a cached copy stays correct — until the homeserver
 * quarantines or deletes it, which the app should eventually honour. So: serve from disk without
 * asking for [freshForMs], then revalidate with the ETag gomuks sends. Unchanged media costs a 304
 * and is fresh again; removed media fails and the avatar falls back to initials.
 *
 * (Coil's default serves the disk copy forever and even caches 404s, which would pin removed media
 * — and a transient 404 — for good.) Error responses are never written to disk here.
 */
@OptIn(ExperimentalCoilApi::class)
class MediaCacheStrategy(
    private val freshForMs: Long = THIRTY_DAYS_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        val age = clock() - cacheResponse.responseMillis
        if (age in 0 until freshForMs) return CacheStrategy.ReadResult(cacheResponse)
        val etag = cacheResponse.headers["ETag"] ?: return CacheStrategy.ReadResult(networkRequest)
        val conditional =
            networkRequest.headers
                .newBuilder()
                .set("If-None-Match", etag)
                .build()
        return CacheStrategy.ReadResult(networkRequest.copy(headers = conditional))
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult =
        when {
            // Still valid: refresh the entry's metadata (and so its age), keep the body on disk.
            networkResponse.code == HTTP_NOT_MODIFIED && cacheResponse != null -> {
                CacheStrategy.WriteResult(
                    networkResponse.copy(
                        headers = cacheResponse.headers.mergedWith(networkResponse.headers),
                        body = null
                    )
                )
            }

            networkResponse.code in HTTP_SUCCESS -> {
                CacheStrategy.WriteResult(networkResponse)
            }

            else -> {
                CacheStrategy.WriteResult.DISABLED
            }
        }

    /** Headers from [newer] replace same-named ones (what Coil's internal `plus` does). */
    private fun NetworkHeaders.mergedWith(newer: NetworkHeaders): NetworkHeaders =
        newBuilder().apply { newer.asMap().forEach { (key, values) -> set(key, values) } }.build()

    companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        private const val HTTP_NOT_MODIFIED = 304
        private val HTTP_SUCCESS = 200 until 300
    }
}
