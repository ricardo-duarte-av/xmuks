package pt.aguiarvieira.xmuks.core.data.media

import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** An upload as a message of ours: its thumbnail (from our cache), progress, or why it failed. */
fun PendingUpload.toTimelineItem(
    me: String,
    myName: String,
): TimelineItem.Message {
    val shownCaption = caption.takeIf { it.isNotBlank() }
    val first = PendingPart(kind, filename, previewFile, width, height, blurhash)
    return TimelineItem.Message(
        key = "u:$id",
        eventId = "u:$id",
        sender = me,
        label = SenderLabel(me, myName),
        senderAvatarMxc = null,
        fromMe = true,
        timestamp = createdAt,
        content =
            if (gallery.isEmpty()) {
                first.content(shownCaption)
            } else {
                MessageContent.Gallery(gallery.map { it.content(null) }, shownCaption, html = null)
            },
        reply = null,
        reactions = emptyList(),
        edited = false,
        firstInGroup = true,
        lastInGroup = true,
        readBy = emptyList(),
        sendError = error,
        sendState = if (error != null) SendState.Failed else SendState.Sending,
        localId = UPLOAD_PREFIX + id,
        uploadProgress = progress.takeIf { error == null },
    )
}

/** A file on its way up as the timeline shows it: its thumbnail from our cache. */
private fun PendingPart.content(caption: String?): MessageContent {
    val media =
        Media(
            mxc = previewFile.orEmpty(),
            encrypted = false,
            mimeType = null,
            width = width,
            height = height,
            size = null,
            blurhash = blurhash,
            thumbnailMxc = previewFile,
            thumbnailEncrypted = false,
            name = filename,
        )
    return when (kind) {
        MediaKind.Image -> MessageContent.Image(media, caption)
        MediaKind.Video -> MessageContent.Video(media, caption)
        MediaKind.Audio, MediaKind.File -> MessageContent.File(media, filename)
    }
}

/** Local IDs of uploads (as opposed to outbox entries): what resend and discard tell apart. */
const val UPLOAD_PREFIX = "u:"
