package pt.aguiarvieira.xmuks.feature.room

import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/** Resolves `mxc://` for the timeline: avatars as thumbnails, media as full files (decrypted by gomuks). */
class MediaResolver(
    val avatar: (String?) -> String?,
    val media: (mxc: String, encrypted: Boolean) -> String?,
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
