package pt.aguiarvieira.xmuks.feature.room

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** A video that plays in its bubble: the room's player, which one it is, and the way out to fullscreen. */
internal class InlineVideo(
    val player: InlinePlayer,
    val key: String,
    val onFullscreen: () -> Unit,
)

/**
 * Over a video's thumbnail: while it's the one loaded, the picture itself, then the play/pause
 * state (a spinner while it buffers) and its position; always, the fullscreen button.
 */
@Composable
internal fun BoxScope.InlineVideoLayer(video: InlineVideo) {
    val now by video.player.state.collectAsState()
    val mine = now?.takeIf { it.key == video.key }
    // Scrolled away (or the room left): it stops.
    DisposableEffect(video.key) { onDispose { video.player.pause(video.key) } }
    if (mine != null) VideoSurface(video.player)
    when {
        mine?.buffering == true -> {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
        }

        mine?.playing != true -> {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = SCRIM_ALPHA)) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
        }
    }
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = SCRIM_ALPHA),
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
    ) {
        IconButton(onClick = video.onFullscreen, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.fullscreen), tint = Color.White)
        }
    }
    if (mine != null && mine.durationMs > 0) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = SCRIM_ALPHA),
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
            Text(
                "${clock(mine.positionMs)} / ${clock(mine.durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The player's picture, fitted into the bubble; detached again when this bubble goes. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: InlinePlayer) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player.player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

private const val SCRIM_ALPHA = 0.5f
