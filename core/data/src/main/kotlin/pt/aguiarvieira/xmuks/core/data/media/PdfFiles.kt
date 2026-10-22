package pt.aguiarvieira.xmuks.core.data.media

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * PDFs, downloaded whole (the platform's renderer needs the file) into [dir] and kept there, and
 * their pages drawn: the timeline's preview of the first page, and the viewer's pages.
 */
class PdfFiles(
    private val http: OkHttpClient,
    private val dir: File,
    private val io: CoroutineDispatcher,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** [url]'s file, downloaded once; it fails past [maxBytes]. */
    suspend fun file(
        url: String,
        maxBytes: Long = VIEWER_BYTES,
    ): File =
        withContext(io) {
            val file = File(dir, "${sha1(url)}.pdf")
            locks.getOrPut(url) { Mutex() }.withLock {
                if (!file.exists()) download(url, file, maxBytes)
            }
            file.also { it.setLastModified(System.currentTimeMillis()) }
        }

    /** How many pages [url] has. */
    suspend fun pageCount(url: String): Int = withContext(io) { open(file(url)) { it.pageCount } }

    /** Page [index] of [file], [width] pixels wide, on white (a page's background is see-through). */
    fun render(
        file: File,
        index: Int,
        width: Int,
    ): Bitmap =
        open(file) { renderer ->
            renderer.openPage(index).use { page ->
                val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

    private fun <T> open(
        file: File,
        use: (PdfRenderer) -> T,
    ): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(use)
        }

    private fun download(
        url: String,
        file: File,
        maxBytes: Long,
    ) {
        dir.mkdirs()
        trim()
        val part = File(dir, "${file.name}.part")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val problem =
                when {
                    !response.isSuccessful -> "HTTP ${response.code}"
                    response.body.contentLength() > maxBytes -> TOO_BIG
                    else -> null
                }
            if (problem != null) throw IOException(problem)
            part.outputStream().use { out -> copyAtMost(response.body.byteStream(), out, maxBytes) }
        }
        part.renameTo(file)
    }

    /** All of [input] into [out], failing once past [maxBytes] (a server may not say the length). */
    private fun copyAtMost(
        input: InputStream,
        out: OutputStream,
        maxBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            total += read
            if (total > maxBytes) throw IOException(TOO_BIG)
            out.write(buffer, 0, read)
            read = input.read(buffer)
        }
    }

    /** Least recently used out, past [KEEP_BYTES]. */
    private fun trim() {
        val files = dir.listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (old in files) {
            if (total <= KEEP_BYTES) break
            total -= old.length()
            old.delete()
        }
    }

    /**
     * Coil loads a PDF's pages from its URL with a fragment: [PREVIEW] for the timeline's look at
     * the first page (only for a file up to [PREVIEW_BYTES]), `page=N` for the viewer's page N.
     */
    fun fetcher(): Fetcher.Factory<Uri> = Pages()

    private inner class Pages : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val fragment = data.fragment ?: return null
            val preview = fragment == PREVIEW
            val page = if (preview) 0 else fragment.takeIf { it.startsWith(PAGE) }?.removePrefix(PAGE)?.toIntOrNull()
            if (page == null || (data.scheme != "https" && data.scheme != "http")) return null
            return PageFetcher(data.toString().substringBefore('#'), page, preview)
        }
    }

    private inner class PageFetcher(
        private val url: String,
        private val page: Int,
        private val preview: Boolean,
    ) : Fetcher {
        override suspend fun fetch(): ImageFetchResult {
            val file = file(url, if (preview) PREVIEW_BYTES else VIEWER_BYTES)
            val bitmap = withContext(io) { render(file, page, if (preview) PREVIEW_WIDTH else PAGE_WIDTH) }
            return ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
        }
    }

    companion object {
        const val PREVIEW = "pdf"
        private const val PAGE = "page="

        /** What loads the timeline's look at [url]'s first page. */
        fun preview(url: String) = "$url#$PREVIEW"

        /** What loads [url]'s page [index] for the viewer. */
        fun page(
            url: String,
            index: Int,
        ) = "$url#$PAGE$index"

        /** A PDF previewed in the timeline without being asked: up to this big. */
        const val PREVIEW_BYTES = 20L * 1024 * 1024
        private const val VIEWER_BYTES = 200L * 1024 * 1024
        private const val PREVIEW_WIDTH = 720
        private const val PAGE_WIDTH = 1600
        private const val KEEP_BYTES = 128L * 1024 * 1024
        private const val BUFFER = 64 * 1024
        private const val TOO_BIG = "Too big to preview"

        private fun sha1(text: String) =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
