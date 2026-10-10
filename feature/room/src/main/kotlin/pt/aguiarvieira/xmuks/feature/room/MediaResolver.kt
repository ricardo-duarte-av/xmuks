package pt.aguiarvieira.xmuks.feature.room

import coil3.ImageLoader
import pt.aguiarvieira.xmuks.core.data.timeline.FileKeys
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/** Resolves `mxc://` for the timeline: avatars as thumbnails, media as full files (decrypted by gomuks). */
class MediaResolver(
    val avatar: (String?) -> String?,
    private val url: (mxc: String, encrypted: Boolean, keys: FileKeys?) -> String?,
    /** Loads timeline pictures into their own cache tier; null uses the app's default loader. */
    val images: ImageLoader? = null,
) {
    /** An avatar or other unencrypted image, full size in the viewer, its thumbnail shown meanwhile. */
    fun image(
        mxc: String?,
        title: String?,
        subtitle: String? = null,
    ): ViewerMedia? {
        val url = mxc?.let { url(it, false, null) } ?: return null
        return ViewerMedia(ViewerMedia.Kind.Image, url, previewUrl = avatar(mxc), title = title, subtitle = subtitle)
    }

    /** Unencrypted media by its `mxc://` URI alone (inline images, link previews). */
    fun media(
        mxc: String,
        encrypted: Boolean,
    ): String? = url(mxc, encrypted, null)

    /** A message's file. */
    fun file(media: Media): String? = url(media.mxc, media.encrypted, media.keys)

    /** A message's thumbnail, if its sender attached one. */
    fun thumbnail(media: Media): String? =
        media.thumbnailMxc?.let { url(it, media.thumbnailEncrypted, media.thumbnailKeys) }
}
