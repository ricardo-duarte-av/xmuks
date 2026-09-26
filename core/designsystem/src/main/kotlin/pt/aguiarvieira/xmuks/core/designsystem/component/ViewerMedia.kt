package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Anything the full-screen media viewer can show: timeline images, videos and audio today; user
 * and room avatars later. URLs are ready to load (gomuks media URLs, decryption included).
 */
@Immutable
@Serializable
data class ViewerMedia(
    val kind: Kind,
    /** The original file. */
    val url: String,
    /** Something already on screen (a thumbnail), shown at once while [url] loads. */
    val previewUrl: String? = null,
    val blurhash: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Top bar text, e.g. the sender and the time. */
    val title: String? = null,
    val subtitle: String? = null,
    /** Shared-element key of the element the viewer opened from, so it grows out of it. */
    val sharedKey: String? = null,
) {
    enum class Kind { Image, Video, Audio }
}
