package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import java.text.DateFormat
import java.util.Date

internal fun viewerMedia(
    message: TimelineItem.Message,
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
    /** The shared-element key's ID: the event's, or one gallery item's (see [galleryKey]). */
    key: String = message.eventId,
    /** Which of an MSC4274 gallery's items it is. */
    index: Int? = null,
) = ViewerMedia(
    kind = kind,
    url = resolver.file(media).orEmpty(),
    previewUrl = timelineSource(media, kind, resolver),
    blurhash = media.blurhash,
    width = media.width,
    height = media.height,
    title = message.senderName,
    subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(message.timestamp)),
    sharedKey = SharedKeys.media(key),
    name = media.name,
    mimeType = media.mimeType,
    // One still in our outbox has no event to forward or go to yet. The room is the screen's to add.
    eventId = message.eventId.takeIf { message.localId == null },
    index = index,
)

/** A sent message's one file, as forwarding takes it (galleries and stickers aren't forwarded). */
internal fun forwardable(
    message: TimelineItem.Message,
    resolver: MediaResolver,
): ViewerMedia? {
    if (message.localId != null || message.uploadProgress != null) return null
    val (media, kind) =
        when (val c = message.content) {
            is MessageContent.Image -> c.media to ViewerMedia.Kind.Image
            is MessageContent.Video -> c.media to ViewerMedia.Kind.Video
            is MessageContent.Audio -> c.media to ViewerMedia.Kind.Audio
            is MessageContent.File -> c.media to if (c.media.isPdf) ViewerMedia.Kind.Pdf else ViewerMedia.Kind.File
            else -> return null
        }
    return viewerMedia(message, media, kind, resolver)
}

/** A message's media with its room, which the viewer's actions on it (forwarding, going to it) need. */
internal fun ViewerMedia.inRoom(roomId: String) = if (eventId != null) copy(roomId = roomId) else this

/**
 * What the timeline loads: the sender's thumbnail when there is one (gomuks only makes avatar
 * thumbnails itself), else the original for images — Coil downsamples it to the bubble. A GIF
 * loads itself only when it's to [animate]: otherwise its still thumbnail, or nothing (a GIF can be
 * many megabytes, to show as a still).
 */
internal fun timelineSource(
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
    animate: Boolean = false,
): String? {
    val original = resolver.file(media)
    if (media.animated && animate) return original
    return resolver.thumbnail(media)
        ?: original.takeIf { kind == ViewerMedia.Kind.Image && !media.animated }
}
