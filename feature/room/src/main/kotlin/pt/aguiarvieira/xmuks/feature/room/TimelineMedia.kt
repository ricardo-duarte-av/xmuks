package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import pt.aguiarvieira.xmuks.core.data.timeline.Media
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
)

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
