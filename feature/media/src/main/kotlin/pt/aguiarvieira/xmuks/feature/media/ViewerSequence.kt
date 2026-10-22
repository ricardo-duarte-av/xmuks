package pt.aguiarvieira.xmuks.feature.media

import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.timeline.GalleryItem
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import java.text.DateFormat
import java.util.Date

/** What a swipe can bring in: pictures and videos. */
internal val ViewerMedia.Kind.swipeable: Boolean
    get() = this == ViewerMedia.Kind.Image || this == ViewerMedia.Kind.Video

/** One piece of media's place in a room: its event, and which of a gallery's items. */
internal val ViewerMedia.pageKey: String
    get() = if (index != null) "$eventId#$index" else eventId ?: url

/**
 * A room's picture or video as the viewer shows it, from its gallery page; null for anything
 * else, and for a spoiler (it waits for a tap in the timeline, not to be swiped into view).
 */
internal fun GalleryItem.toViewer(
    roomId: String,
    urls: MediaUrls,
): ViewerMedia? {
    val (media, kind) =
        when (val c = content) {
            is MessageContent.Image -> c.media to ViewerMedia.Kind.Image
            is MessageContent.Video -> c.media to ViewerMedia.Kind.Video
            else -> return null
        }
    val original = urls.file(media)?.takeUnless { media.spoiler } ?: return null
    val thumbnail = media.thumbnailMxc?.let { urls.media(it, media.thumbnailEncrypted, media.thumbnailKeys) }
    return ViewerMedia(
        kind = kind,
        url = original,
        previewUrl = thumbnail ?: original.takeIf { kind == ViewerMedia.Kind.Image && !media.animated },
        blurhash = media.blurhash,
        width = media.width,
        height = media.height,
        title = senderName,
        subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp)),
        name = media.name,
        mimeType = media.mimeType,
        roomId = roomId,
        eventId = eventId,
        index = key.substringAfter('#', "").toIntOrNull(),
    )
}
