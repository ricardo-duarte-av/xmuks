package pt.aguiarvieira.xmuks.core.data.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Sharing media to other apps (the system's share sheet): the file, decrypted by gomuks as it
 * serves it, is copied into [dir] and handed over as a content URI of our [SharedFiles] provider.
 * A PDF already kept whole by [pdf] is copied from there. Copies are cleared a day later: when the
 * other app is done reading isn't known. [file] makes such a copy for xmuks' own use (resizing a
 * forwarded picture).
 */
class MediaShares(
    private val context: Context,
    private val http: OkHttpClient,
    private val urls: MediaUrls,
    private val pdf: PdfFiles,
    private val dir: File,
    private val io: CoroutineDispatcher,
) {
    /** Where [media] downloads from: what [intent] takes. */
    fun urlOf(media: Media): String? = urls.file(media)

    /** The share sheet for [url]'s file, saved as [name], with [text] (a caption) alongside. */
    suspend fun intent(
        url: String,
        name: String,
        mimeType: String?,
        text: String? = null,
    ): Intent {
        val file = file(url, name)
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY, file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType?.takeIf { it.contains('/') } ?: OCTET_STREAM
                putExtra(Intent.EXTRA_STREAM, uri)
                if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                // The grant travels with the clip, through the chooser to the app picked.
                clipData = ClipData.newRawUri(name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, null)
    }

    /** [url]'s file, downloaded under [name]. */
    suspend fun file(
        url: String,
        name: String,
    ): File = withContext(io) { copy(url, name) }

    private fun copy(
        url: String,
        name: String,
    ): File {
        dir.mkdirs()
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > KEEP_MS }?.forEach {
            it.deleteRecursively()
        }
        // A folder each, so the file keeps its own name (what the other app shows).
        val safe = name.takeUnless { it.isBlank() || it == "." || it == ".." || it.contains('/') } ?: "file"
        val target = File(File(dir, UUID.randomUUID().toString()).apply { mkdirs() }, safe)
        try {
            val cached = pdf.cached(url)
            if (cached != null) {
                cached.copyTo(target, overwrite = true)
            } else {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    target.outputStream().use { response.body.byteStream().copyTo(it) }
                }
            }
        } catch (e: IOException) {
            target.parentFile?.deleteRecursively()
            throw e
        }
        return target
    }

    companion object {
        /** The share sheet for a message's [text]. */
        fun textIntent(text: String): Intent =
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                null,
            )

        /** Our provider's authority, after the package name; the manifest says the same. */
        const val AUTHORITY = ".shares"
        private const val OCTET_STREAM = "application/octet-stream"
        private const val KEEP_MS = 24L * 60 * 60 * 1000
    }
}

/** Serves shared copies to other apps (its own class: one FileProvider per app manifest name). */
class SharedFiles : FileProvider()
