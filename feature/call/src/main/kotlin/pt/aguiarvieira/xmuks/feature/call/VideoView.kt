package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** One video track, cropped to fill; our own camera mirrored, as people expect of a selfie view. */
@Composable
internal fun VideoView(
    track: VideoTrack,
    room: Room,
    mirror: Boolean,
    modifier: Modifier = Modifier,
) {
    var view by remember(room) { mutableStateOf<TextureViewRenderer?>(null) }
    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).also {
                room.initVideoRenderer(it)
                it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                view = it
            }
        },
        update = { it.setMirror(mirror) },
        onRelease = { it.release() },
        modifier = modifier,
    )
    val target = view
    DisposableEffect(track, target) {
        if (target != null) track.addRenderer(target)
        onDispose { if (target != null) track.removeRenderer(target) }
    }
}
