package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * A room, person or space avatar: the image when there is one and it loads, the initials tile
 * (same colour everywhere for the same [id]) while loading, on failure, or when there's none.
 */
@Composable
fun RoomAvatar(
    name: String,
    id: String,
    avatarUrl: String?,
    kind: AvatarKind,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val shape = kind.shape()
    // Initials sit under the image until it has loaded. Then a light neutral replaces them, in both
    // themes: transparent logos are nearly always dark glyphs made for light pages, so they stay
    // legible in dark mode, and no initials colour shows through as a ring (seen on-device).
    var loaded by remember(avatarUrl) { mutableStateOf(false) }
    Box(
        modifier =
            modifier.size(size).graphicsLayer {
                this.shape = shape
                clip = true
                // The cookie outline is costly to clip every frame; as a cached layer it is drawn
                // once and scrolling just moves the texture (measured: the Spaces grid was GPU-bound).
                if (kind == AvatarKind.Space) compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        if (loaded) {
            val colors = MaterialTheme.colorScheme
            val dark = colors.surface.luminance() < HALF
            Box(Modifier.size(size).background(if (dark) colors.inverseSurface else colors.surfaceContainerHighest))
        } else {
            InitialsAvatar(name = name, id = id, kind = kind, size = size)
        }
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.size(size),
            )
        }
    }
}

private const val HALF = 0.5f
