package pt.aguiarvieira.xmuks.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A file the user picked (or the camera made), before anything is done to it. */
data class PickedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val kind: MediaKind,
)

enum class MediaKind { Image, Video, Audio, File }

/** How big a sent image may be: its longest side, or as it is. */
@Suppress("MagicNumber") // the sizes are the definition
enum class ImageSize(
    val maxSide: Int?,
) {
    Original(null),
    Large(2048),
    Medium(1280),
    Small(640),
}

/** A thumbnail made on the phone: JPEG bytes, its size, and its blurhash. */
class Thumbnail(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val blurhash: String,
    /** The same image in our cache, for the timeline to show while it uploads. */
    val file: File,
)

/** Ready to upload. [width]/[height] are what the receiver will see (rotation applied). */
class PreparedMedia(
    val source: UploadSource,
    val filename: String,
    val mimeType: String?,
    val kind: MediaKind,
    val width: Int?,
    val height: Int?,
    val thumbnail: Thumbnail?,
    /** A voice message: gomuks adds the waveform and marks it as voice (MSC3245). */
    val voice: Boolean = false,
)

/**
 * Turns picked files into uploads: images resized (re-encoded, which also drops EXIF, location
 * included) or sent as they are, a thumbnail and its blurhash for images and videos (a video's at
 * a quarter of its length), everything else streamed as is.
 */
class MediaPreparer(
    private val context: Context,
    private val io: CoroutineContext,
) {
    private val resolver get() = context.contentResolver
    private val dir: File get() = File(context.cacheDir, "uploads").apply { mkdirs() }

    suspend fun inspect(uri: Uri): PickedFile =
        withContext(io) {
            var name: String? = null
            var size = -1L
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            resolver.query(uri, columns, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val mime = resolver.getType(uri)
            PickedFile(uri, name ?: uri.lastPathSegment ?: "file", mime, size, kindOf(mime))
        }

    /** Whether resizing makes sense: still images only (animation would be lost), not vectors. */
    fun resizable(file: PickedFile) = file.kind == MediaKind.Image && file.mimeType !in NOT_RESIZABLE

    /** The image at [size] (null keeps it as picked); its bytes, to size the choice before sending. */
    suspend fun resized(
        file: PickedFile,
        size: ImageSize,
    ): File? =
        withContext(io) {
            val maxSide = size.maxSide ?: return@withContext null
            val bitmap = decode(file.uri, maxSide)
            val png = file.mimeType == "image/png"
            val out = File(dir, "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
            out.outputStream().use {
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            bitmap.recycle()
            out
        }

    /** What gets uploaded for [file], [resizedTo] standing in for an image when it was resized. */
    suspend fun prepare(
        file: PickedFile,
        resizedTo: File? = null,
    ): PreparedMedia =
        withContext(io) {
            when (file.kind) {
                MediaKind.Image -> image(file, resizedTo)
                MediaKind.Video -> video(file)
                else -> PreparedMedia(streamOf(file), file.name, file.mimeType, file.kind, null, null, null)
            }
        }

    private fun image(
        file: PickedFile,
        resizedTo: File?,
    ): PreparedMedia {
        val uri = resizedTo?.let(Uri::fromFile) ?: file.uri
        val thumbnail = runCatching { thumbnailOf(decode(uri, THUMBNAIL_SIDE)) }.getOrNull()
        return if (resizedTo == null) {
            PreparedMedia(streamOf(file), file.name, file.mimeType, MediaKind.Image, null, null, thumbnail)
        } else {
            val bounds = decodeBounds(uri)
            val png = resizedTo.extension == "png"
            PreparedMedia(
                UploadSource(resizedTo.length()) { resizedTo.inputStream() },
                file.name.substringBeforeLast('.') + if (png) ".png" else ".jpg",
                if (png) "image/png" else "image/jpeg",
                MediaKind.Image,
                bounds?.first,
                bounds?.second,
                thumbnail,
            )
        }
    }

    /** The frame a quarter of the way in, as the thumbnail; dimensions with rotation applied. */
    private fun video(file: PickedFile): PreparedMedia {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)

            fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            val durationMs = number(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0L
            var w = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            var h = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            if (rotation % HALF_TURN != 0) w = h.also { h = w }
            // Scaled to fit the box, aspect kept (and rotation applied).
            val frame =
                retriever.getScaledFrameAtTime(
                    durationMs * QUARTER_US,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_SIDE,
                    THUMBNAIL_SIDE,
                )
            val thumbnail = frame?.let { runCatching { thumbnailOf(it) }.getOrNull() }
            return PreparedMedia(streamOf(file), file.name, file.mimeType, MediaKind.Video, w, h, thumbnail)
        } finally {
            retriever.release()
        }
    }

    /** JPEG thumbnail of [bitmap] (already thumbnail-sized), and a blurhash from a tiny copy of it. */
    private fun thumbnailOf(bitmap: Bitmap): Thumbnail {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        val bytes = out.toByteArray()
        val scale = BLURHASH_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val small =
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).roundToInt()),
                max(1, (bitmap.height * scale).roundToInt()),
                true,
            )
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val hash = BlurhashEncoder.encode(pixels, small.width, small.height)
        val file = File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
        return Thumbnail(bytes, bitmap.width, bitmap.height, hash, file)
    }

    /** Decodes with EXIF rotation applied, scaled so the longest side is at most [maxSide]. */
    private fun decode(
        uri: Uri,
        maxSide: Int,
    ): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val size = info.size
            val scale = minOf(1f, maxSide.toFloat() / max(size.width, size.height))
            decoder.setTargetSize(
                max(1, (size.width * scale).roundToInt()),
                max(1, (size.height * scale).roundToInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeBounds(uri: Uri): Pair<Int, Int>? =
        runCatching {
            var bounds: Pair<Int, Int>? = null
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                bounds = info.size.width to info.size.height
                decoder.setTargetSize(1, 1)
            }
            bounds
        }.getOrNull()

    private fun streamOf(file: PickedFile) =
        UploadSource(
            file.size
        ) { resolver.openInputStream(file.uri) ?: throw FileNotFoundException(file.uri.toString()) }

    /** An image's size as it will show (rotation applied), for offering sizes; null if unreadable. */
    suspend fun dimensions(file: PickedFile): Pair<Int, Int>? = withContext(io) { decodeBounds(file.uri) }

    /** A still for previewing a video before it's sent: the frame a quarter of the way in. */
    suspend fun videoPreview(file: PickedFile): File? =
        withContext(io) { runCatching { video(file).thumbnail?.file }.getOrNull() }

    /** A recording of ours, sent as a voice message. */
    fun voice(file: File): PreparedMedia =
        PreparedMedia(
            UploadSource(file.length()) { file.inputStream() },
            file.name,
            VOICE_MIME,
            MediaKind.Audio,
            null,
            null,
            null,
            voice = true,
        )

    /** Clears what earlier sends left in the cache. */
    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val NOT_RESIZABLE = setOf("image/gif", "image/webp", "image/svg+xml", "image/apng")
        const val JPEG_QUALITY = 85
        const val THUMBNAIL_QUALITY = 80
        const val THUMBNAIL_SIDE = 800
        const val BLURHASH_SIDE = 32
        const val HALF_TURN = 180
        const val VOICE_MIME = "audio/ogg"

        /** Milliseconds to the microseconds a quarter of the way in: ms × 1000 / 4. */
        const val QUARTER_US = 250L

        fun kindOf(mime: String?): MediaKind =
            when {
                mime == null -> MediaKind.File
                mime == "image/svg+xml" -> MediaKind.File
                mime.startsWith("image/") -> MediaKind.Image
                mime.startsWith("video/") -> MediaKind.Video
                mime.startsWith("audio/") -> MediaKind.Audio
                else -> MediaKind.File
            }
    }
}
