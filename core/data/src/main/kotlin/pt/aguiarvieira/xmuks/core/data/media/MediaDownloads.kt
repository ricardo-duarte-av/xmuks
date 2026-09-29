package pt.aguiarvieira.xmuks.core.data.media

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** How a download ended, for the screen to say. */
sealed interface DownloadResult {
    val name: String

    data class Saved(
        override val name: String,
    ) : DownloadResult

    data class Failed(
        override val name: String,
        val reason: String,
    ) : DownloadResult
}

/**
 * Saves media where the user chose (a document from the system's "save as": no storage
 * permission needed). gomuks decrypts encrypted media as it serves it. Runs in the app's scope, so
 * leaving the room doesn't cut a download short.
 */
class MediaDownloads(
    private val http: OkHttpClient,
    private val urls: MediaUrls,
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _results = MutableSharedFlow<DownloadResult>(extraBufferCapacity = RESULTS)
    val results: SharedFlow<DownloadResult> = _results.asSharedFlow()

    fun save(
        mxc: String,
        encrypted: Boolean,
        name: String,
        target: Uri,
    ) {
        scope.launch {
            val result =
                runCatching { download(mxc, encrypted, target) }
                    .fold(
                        onSuccess = { DownloadResult.Saved(name) },
                        onFailure = { DownloadResult.Failed(name, it.message ?: it.javaClass.simpleName) },
                    )
            _results.emit(result)
        }
    }

    private suspend fun download(
        mxc: String,
        encrypted: Boolean,
        target: Uri,
    ) = withContext(io) {
        val url = urls.media(mxc, encrypted) ?: throw IOException("Not a media link")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val out = resolver.openOutputStream(target, "wt") ?: throw IOException("Can't write there")
                out.use { response.body.byteStream().copyTo(it) }
            }
        } catch (e: IOException) {
            // Nothing half-written left behind under the chosen name.
            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
    }

    private companion object {
        const val RESULTS = 8
    }
}
