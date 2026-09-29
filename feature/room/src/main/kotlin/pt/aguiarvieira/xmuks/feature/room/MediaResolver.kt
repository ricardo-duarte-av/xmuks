package pt.aguiarvieira.xmuks.feature.room

import coil3.ImageLoader
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/** Resolves `mxc://` for the timeline: avatars as thumbnails, media as full files (decrypted by gomuks). */
class MediaResolver(
    val avatar: (String?) -> String?,
    val media: (mxc: String, encrypted: Boolean) -> String?,
    /** Loads timeline pictures into their own cache tier; null uses the app's default loader. */
    val images: ImageLoader? = null,
) {
    /** An avatar or other unencrypted image, full size in the viewer, its thumbnail shown meanwhile. */
    fun image(
        mxc: String?,
        title: String?,
        subtitle: String? = null,
    ): ViewerMedia? {
        val url = mxc?.let { media(it, false) } ?: return null
        return ViewerMedia(ViewerMedia.Kind.Image, url, previewUrl = avatar(mxc), title = title, subtitle = subtitle)
    }
}
