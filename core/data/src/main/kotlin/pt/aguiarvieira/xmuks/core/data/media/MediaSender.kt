package pt.aguiarvieira.xmuks.core.data.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.SPOILER
import pt.aguiarvieira.xmuks.core.data.timeline.messageParams
import pt.aguiarvieira.xmuks.core.data.timeline.obj
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** A file on its way up, as the timeline shows it until the message is in the outbox. */
data class PendingUpload(
    val id: String,
    val roomId: String,
    val createdAt: Long,
    val kind: MediaKind,
    val filename: String,
    val caption: String,
    /** Our thumbnail in the cache, shown until the real media is there. */
    val previewFile: String?,
    val width: Int?,
    val height: Int?,
    val blurhash: String?,
    val progress: Float = 0f,
    /** Why the last attempt failed; it waits for a retry or a discard. */
    val error: String? = null,
)

/**
 * Sends media: the thumbnail (made on the phone) and the file go up through gomuks, our thumbnail
 * and blurhash go into gomuks' content, and the message joins the outbox like any other (so it's
 * never sent twice). Uploads live in memory: one cut short by the app dying is lost, never half-sent.
 */
class MediaSender(
    private val uploader: MediaUploader,
    private val outbox: Outbox,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountScoped {
    private class Upload(
        val shown: PendingUpload,
        val media: PreparedMedia,
        val replyTo: ReplyTarget?,
        val encrypt: Boolean,
        /** Sent as a spoiler (MSC4193): hidden until tapped. */
        val spoiler: Boolean,
        val job: Job? = null,
        /** Order of sending: messages go out in it, however their uploads race. */
        val seq: Long = 0,
    )

    private val sequence = AtomicLong()

    private val uploads = MutableStateFlow<Map<String, Upload>>(emptyMap())

    fun observe(roomId: String): Flow<List<PendingUpload>> =
        uploads
            .map { all ->
                all.values
                    .map { it.shown }
                    .filter { it.roomId == roomId }
                    .sortedBy { it.createdAt }
            }.distinctUntilChanged()

    fun send(
        roomId: String,
        media: PreparedMedia,
        caption: String,
        replyTo: ReplyTarget?,
        encrypt: Boolean,
        spoiler: Boolean = false,
    ) {
        val thumbnail = media.thumbnail
        val shown =
            PendingUpload(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                createdAt = clock(),
                kind = media.kind,
                filename = media.filename,
                caption = caption,
                previewFile = thumbnail?.file?.toURI()?.toString(),
                width = media.width ?: thumbnail?.width,
                height = media.height ?: thumbnail?.height,
                blurhash = thumbnail?.blurhash,
            )
        start(Upload(shown, media, replyTo, encrypt, spoiler, seq = sequence.incrementAndGet()))
    }

    fun retry(id: String) {
        val upload = uploads.value[id]?.takeIf { it.job?.isActive != true } ?: return
        start(upload)
    }

    fun discard(id: String) {
        uploads.value[id]?.job?.cancel()
        uploads.update { it - id }
    }

    private fun start(upload: Upload) {
        val id = upload.shown.id
        // Registered before it runs: a quick upload must not finish before it's in the list.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                runCatching { upload(upload) }
                    .onSuccess { uploads.update { it - id } }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        change(id) { it.copy(error = e.message ?: e.javaClass.simpleName) }
                    }
            }
        val restarted =
            Upload(
                upload.shown.copy(progress = 0f, error = null),
                upload.media,
                upload.replyTo,
                upload.encrypt,
                upload.spoiler,
                job,
                upload.seq
            )
        uploads.update { it + (id to restarted) }
        job.start()
    }

    private suspend fun upload(upload: Upload) {
        val media = upload.media
        val thumbnail =
            media.thumbnail?.let { t ->
                uploader
                    .upload(
                        UploadSource(t.bytes.size.toLong()) { t.bytes.inputStream() },
                        "thumbnail.jpg",
                        "image/jpeg",
                        upload.encrypt
                    ).getOrThrow()
            }
        val content =
            uploader
                .upload(media.source, media.filename, media.mimeType, upload.encrypt, voiceMessage = media.voice) { p ->
                    change(upload.shown.id) { it.copy(progress = p) }
                }.getOrThrow()
        val roomId = upload.shown.roomId
        // Files picked together arrive in the order they were picked: a small one that finished
        // first waits for those before it (one that failed doesn't hold the rest up).
        uploads.first { all ->
            all.values.none { it.shown.roomId == roomId && it.seq < upload.seq && it.shown.error == null }
        }
        outbox.sendMessage(
            roomId,
            messageParams(
                roomId,
                upload.shown.caption,
                upload.replyTo,
                baseContent = withOurs(content, media, thumbnail),
                extra = extraContent(upload.spoiler),
            )
        )
    }

    private fun change(
        id: String,
        edit: (PendingUpload) -> PendingUpload,
    ) {
        uploads.update { all ->
            val upload = all[id] ?: return@update all
            // Everything but what's shown stays as it was: the job, and the place in the send order.
            all +
                (
                    id to
                        Upload(
                            edit(upload.shown),
                            upload.media,
                            upload.replyTo,
                            upload.encrypt,
                            upload.spoiler,
                            upload.job,
                            upload.seq
                        )
                )
        }
    }

    override suspend fun clearAccountData() {
        uploads.value.values.forEach { it.job?.cancel() }
        uploads.value = emptyMap()
    }

    companion object {
        /**
         * gomuks' content with what we know better: our thumbnail (for a video, instead of gomuks'
         * first frame) with its blurhash, and dimensions with rotation applied.
         */
        fun withOurs(
            content: JsonObject,
            media: PreparedMedia,
            thumbnailUpload: JsonObject?,
        ): JsonObject {
            val info = content.obj("info").orEmpty().toMutableMap()
            media.width?.let { info["w"] = JsonPrimitive(it) }
            media.height?.let { info["h"] = JsonPrimitive(it) }
            val thumbnail = media.thumbnail
            if (thumbnail != null && thumbnailUpload != null) {
                info.remove("thumbnail_url")
                info.remove("thumbnail_file")
                thumbnailUpload["url"]?.let { info["thumbnail_url"] = it }
                thumbnailUpload["file"]?.let { info["thumbnail_file"] = it }
                info["thumbnail_info"] =
                    JsonObject(
                        mapOf(
                            "w" to JsonPrimitive(thumbnail.width),
                            "h" to JsonPrimitive(thumbnail.height),
                            "mimetype" to JsonPrimitive("image/jpeg"),
                            "size" to JsonPrimitive(thumbnail.bytes.size),
                            BLURHASH to JsonPrimitive(thumbnail.blurhash),
                        ),
                    )
                info[BLURHASH] = JsonPrimitive(thumbnail.blurhash)
            }
            return JsonObject(content + ("info" to JsonObject(info)))
        }

        /** What goes beside gomuks' content: the spoiler mark (MSC4193), when it's one. */
        fun extraContent(spoiler: Boolean): JsonObject? =
            if (spoiler) JsonObject(mapOf(SPOILER to JsonPrimitive(true))) else null

        private const val BLURHASH = "xyz.amorgan.blurhash"
    }
}
