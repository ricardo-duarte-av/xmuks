package pt.aguiarvieira.xmuks.feature.room

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.ImageSize
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.PickedFile
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.MediaIssue
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import java.io.File
import kotlin.math.max

/** One way to send an image: at [size], [bytes] big once encoded (null while that's being worked out). */
data class SizeOption(
    val size: ImageSize,
    val width: Int?,
    val height: Int?,
    val bytes: Long?,
)

/** A picked file waiting on the preview screen: what it is, and for images the sizes on offer. */
data class MediaDraft(
    val file: PickedFile,
    /** What to show: the file itself (images), a still (videos), or nothing (the rest). */
    val preview: Any?,
    val options: List<SizeOption> = emptyList(),
    val chosen: ImageSize = ImageSize.Original,
    val sending: Boolean = false,
    /** Preparing it failed (an unreadable file, say). */
    val error: String? = null,
    /** What the bridged network would make of it (nothing when the room isn't bridged). */
    val issues: List<MediaIssue> = emptyList(),
    /** The network drops captions on this kind of file. */
    val captionDropped: Boolean = false,
    /** The longest caption the network shows. */
    val maxCaption: Int? = null,
) {
    /** What stops it reaching the network at all, if anything. */
    val blocked: MediaIssue? get() = issues.firstOrNull { it.blocks }
}

/**
 * The preview-and-send step for attachments: [pick] opens it, sizes are worked out (images, in
 * the background), [send] prepares the chosen version and hands it to the uploads.
 */
class MediaActions(
    private val scope: CoroutineScope,
    private val roomId: String,
    private val preparer: MediaPreparer,
    private val sender: MediaSender,
    private val encrypted: () -> Boolean,
    /** What the message answers (the composer's reply), taken when it's sent. */
    private val replyTo: () -> ReplyTarget?,
    private val onSent: () -> Unit,
    /** gomuks' `upload_dialog`: preview (size, caption) before sending, or send as picked. */
    private val showDialog: () -> Boolean = { true },
    /** What the bridged network here takes; null when nothing is limited. */
    private val features: () -> RoomFeatures? = { null },
    /** A file the network won't take, not sent: said in a toast. */
    private val onLimited: (Limit) -> Unit = {},
) {
    private val _draft = MutableStateFlow<MediaDraft?>(null)
    val draft: StateFlow<MediaDraft?> = _draft.asStateFlow()

    /** Resized versions made so far, by size. */
    private val resized = HashMap<ImageSize, File>()
    private var sizing: Job? = null

    fun pick(uri: Uri) {
        cancel()
        scope.launch {
            val file = preparer.inspect(uri)
            if (!showDialog()) {
                sendAsPicked(file)
                return@launch
            }
            val preview =
                when (file.kind) {
                    MediaKind.Image -> file.uri
                    MediaKind.Video -> preparer.videoPreview(file)
                    else -> null
                }
            _draft.value = withIssues(MediaDraft(file, preview))
            if (preparer.resizable(file)) offerSizes(file)
        }
    }

    /** [draft] with what the network would make of its file. */
    private fun withIssues(draft: MediaDraft): MediaDraft {
        val features = features() ?: return draft
        val msgtype = RoomFeatures.msgtypeOf(draft.file.kind)
        val kind = features.files[msgtype]
        return draft.copy(
            issues = features.checkMedia(msgtype, draft.file.mimeType, draft.file.size),
            captionDropped = kind != null && !kind.caption.works,
            maxCaption = kind?.maxCaptionLength,
        )
    }

    /** Whether the network refuses a [msgtype] file of [mime] and [size] bytes: if so, it's said, not sent. */
    private fun refused(
        msgtype: String,
        mime: String?,
        size: Long,
    ): Boolean {
        val issue = features()?.checkMedia(msgtype, mime, size)?.firstOrNull { it.blocks } ?: return false
        onLimited(Limit.Media(issue))
        return true
    }

    /** Original plus every smaller size; the default is Large when the image is bigger than that. */
    private fun offerSizes(file: PickedFile) {
        sizing =
            scope.launch {
                val (w, h) = preparer.dimensions(file) ?: return@launch
                val longest = max(w, h)
                val smaller = ImageSize.entries.filter { (it.maxSide ?: Int.MAX_VALUE) < longest }
                val options =
                    listOf(SizeOption(ImageSize.Original, w, h, file.size.takeIf { it >= 0 })) +
                        smaller.map { size ->
                            val scale = (size.maxSide ?: longest).toFloat() / longest
                            SizeOption(size, (w * scale).toInt(), (h * scale).toInt(), null)
                        }
                val default =
                    smaller.firstOrNull { it == ImageSize.Large } ?: smaller.firstOrNull() ?: ImageSize.Original
                _draft.update { it?.copy(options = options, chosen = default) }
                // Encode each, largest first, so the choice shows real sizes.
                smaller.forEach { size ->
                    val out = runCatching { preparer.resized(file, size) }.getOrNull() ?: return@forEach
                    resized[size] = out
                    _draft.update { d ->
                        d?.copy(options = d.options.map { if (it.size == size) it.copy(bytes = out.length()) else it })
                    }
                }
            }
    }

    fun choose(size: ImageSize) = _draft.update { it?.copy(chosen = size) }

    fun send(
        caption: String,
        spoiler: Boolean,
    ) {
        val draft = _draft.value?.takeIf { !it.sending } ?: return
        _draft.value = draft.copy(sending = true, error = null)
        scope.launch {
            runCatching {
                val size = draft.chosen
                val file = if (size == ImageSize.Original) null else resized[size] ?: preparer.resized(draft.file, size)
                preparer.prepare(draft.file, file)
            }.onSuccess { prepared ->
                sender.send(roomId, prepared, caption.trim(), replyTo(), encrypted(), spoiler)
                sizing?.cancel()
                resized.clear()
                _draft.value = null
                onSent()
            }.onFailure { e ->
                _draft.update { it?.copy(sending = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** From the keyboard (a GIF, a sticker): a quick action, so no preview step whatever the setting. */
    fun sendNow(uri: Uri) {
        scope.launch { sendAsPicked(preparer.inspect(uri)) }
    }

    /** No preview step: the file as it is, no caption. */
    private suspend fun sendAsPicked(file: PickedFile) {
        if (refused(RoomFeatures.msgtypeOf(file.kind), file.mimeType, file.size)) return
        runCatching { preparer.prepare(file, null) }.onSuccess { prepared ->
            sender.send(roomId, prepared, "", replyTo(), encrypted())
            onSent()
        }
    }

    /** A recording from the voice sheet: straight to the uploads, no preview step. */
    fun sendVoice(file: File) {
        if (refused(RoomFeatures.VOICE, RoomFeatures.VOICE_MIME, file.length())) return
        sender.send(roomId, preparer.voice(file), "", replyTo(), encrypted())
        onSent()
    }

    fun cancel() {
        sizing?.cancel()
        resized.clear()
        _draft.value = null
    }
}
