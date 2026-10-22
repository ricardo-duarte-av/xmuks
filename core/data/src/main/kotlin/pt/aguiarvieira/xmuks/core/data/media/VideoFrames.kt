package pt.aguiarvieira.xmuks.core.data.media

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections

/**
 * A frame of a video that came without a thumbnail, made on the phone: the video's URL with
 * [FRAGMENT] loads, through Coil, a frame from its start. Only what the frame needs is read (the
 * index and the first pictures, by byte ranges, which gomuks serves), up to [MAX_BYTES]; the frame
 * is kept as a JPEG in [dir]. Videos it can't make one of fall back to their blurhash.
 */
class VideoFrames(
    private val http: OkHttpClient,
    private val dir: File,
) : Fetcher.Factory<Uri> {
    /** Videos that gave no frame this run, not tried again. */
    private val failed = Collections.synchronizedSet(mutableSetOf<String>())

    /** Frames being made at once: each reads a video, a few at a time is plenty. */
    private val makers = Semaphore(PARALLEL)

    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        if (data.fragment != FRAGMENT || (data.scheme != "https" && data.scheme != "http")) return null
        val url = data.toString().substringBefore('#')
        return object : Fetcher {
            override suspend fun fetch(): FetchResult = frame(url)
        }
    }

    private suspend fun frame(url: String): FetchResult {
        val file = File(dir, "${sha1(url)}.jpg")
        if (!file.exists()) {
            if (url in failed) throw IOException("No frame for this video")
            makers.withPermit { if (!file.exists()) make(url, file) }
        }
        return SourceFetchResult(ImageSource(file.toOkioPath(), FileSystem.SYSTEM), "image/jpeg", DataSource.DISK)
    }

    private fun make(
        url: String,
        file: File,
    ) {
        val frame =
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(RangeSource(http, url))
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIDE, SIDE)
                }
            }.getOrNull()
        if (frame == null) {
            failed += url
            throw IOException("No frame for this video")
        }
        dir.mkdirs()
        trim()
        val part = File(dir, "${file.name}.part")
        part.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        frame.recycle()
        part.renameTo(file)
    }

    /** Oldest frames out, past [KEEP_BYTES]. */
    private fun trim() {
        val frames = dir.listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = frames.sumOf { it.length() }
        for (old in frames) {
            if (total <= KEEP_BYTES) break
            total -= old.length()
            old.delete()
        }
    }

    companion object {
        const val FRAGMENT = "frame"

        /** What loads [url]'s frame. */
        fun of(url: String) = "$url#$FRAGMENT"

        private const val SIDE = 640
        private const val QUALITY = 80
        private const val PARALLEL = 2
        private const val KEEP_BYTES = 32L * 1024 * 1024

        private fun sha1(text: String) =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * A video read by byte ranges, a block at a time (the few last ones kept), for the platform's
 * retriever: it reads the index (at either end) and the first frames, not the whole file.
 */
private class RangeSource(
    private val http: OkHttpClient,
    private val url: String,
) : MediaDataSource() {
    /** The file's length, once the first range has said it. */
    private var total = -1L
    private var fetched = 0L
    private val blocks =
        object : LinkedHashMap<Long, ByteArray>(BLOCKS, LOAD, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>) = size > BLOCKS
        }

    override fun getSize(): Long {
        if (total < 0) block(0)
        return total
    }

    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position >= getSize()) return -1
        var copied = 0
        while (copied < length && position + copied < total) {
            val at = position + copied
            val block = block(at / BLOCK)
            val from = (at % BLOCK).toInt()
            val count = minOf(length - copied, block.size - from)
            if (count <= 0) break
            System.arraycopy(block, from, buffer, offset + copied, count)
            copied += count
        }
        return copied
    }

    private fun block(index: Long): ByteArray =
        blocks.getOrPut(index) {
            if (fetched > MAX_BYTES) throw IOException("Too much of the video read for a frame")
            val start = index * BLOCK
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Range", "bytes=$start-${start + BLOCK - 1}")
                    .build()
            http.newCall(request).execute().use { response ->
                // A whole file instead of a range would mean reading all of it: not for a frame.
                if (response.code != PARTIAL) throw IOException("No byte ranges (HTTP ${response.code})")
                total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: throw IOException("No size")
                response.body.bytes().also { fetched += it.size }
            }
        }

    override fun close() = blocks.clear()

    private companion object {
        const val BLOCK = 256L * 1024
        const val BLOCKS = 16
        const val LOAD = 0.75f
        const val PARTIAL = 206
        const val MAX_BYTES = 24L * 1024 * 1024
    }
}
