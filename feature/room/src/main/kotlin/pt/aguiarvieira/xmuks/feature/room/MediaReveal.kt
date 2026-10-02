package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import pt.aguiarvieira.xmuks.core.data.timeline.Media

/** Whether a timeline picture is shown yet, and whether a GIF plays; a tap does the next step. */
internal class MediaReveal(
    val revealed: Boolean,
    val animate: Boolean,
    /** What a tap does before the picture opens: reveal it, or play the GIF. Null: it opens. */
    val onTap: (() -> Unit)?,
)

/**
 * Without previews, or when its sender marked it a spoiler, a picture waits (blurhash) for a tap;
 * our own uploads always show. A GIF plays
 * on its own with autoplay, or once tapped: the first tap plays it, the next opens it.
 */
@Composable
internal fun rememberReveal(
    eventId: String,
    media: Media,
    uploading: Boolean,
): MediaReveal {
    val display = LocalMediaDisplay.current
    var tapped by rememberSaveable(eventId) { mutableStateOf(false) }
    var playing by rememberSaveable(eventId) { mutableStateOf(false) }
    val revealed = (display.showPreviews && !media.spoiler) || tapped || uploading
    val animate = media.animated && (display.autoplayGifs || playing)
    val onTap: (() -> Unit)? =
        when {
            !revealed -> ({ tapped = true })
            media.animated && !animate -> ({ playing = true })
            else -> null
        }
    return MediaReveal(revealed, animate, onTap)
}
