package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Anything the full-screen media viewer can show: timeline images, videos, audio and PDFs, user
 * and room avatars. URLs are ready to load (gomuks media URLs, decryption included).
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
    /** The file's name and type: what saving it suggests. */
    val name: String? = null,
    val mimeType: String? = null,
    /**
     * The message it's from (null for avatars and the like): what forwarding sends, "go to message"
     * opens, and the neighbours a swipe brings in come from.
     */
    val roomId: String? = null,
    val eventId: String? = null,
    /** Which of an MSC4274 gallery's items it is; null for a message's one file. */
    val index: Int? = null,
) {
    enum class Kind { Image, Video, Audio, Pdf }

    /** Shown from a message, with its room known: the actions that need one are offered. */
    val fromMessage: Boolean get() = roomId != null && eventId != null
}
