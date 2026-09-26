package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

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
    Box(modifier = modifier.size(size).clip(shape)) {
        if (avatarUrl == null) {
            InitialsAvatar(name = name, id = id, kind = kind, size = size)
        } else {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { InitialsAvatar(name = name, id = id, kind = kind, size = size) },
                error = { InitialsAvatar(name = name, id = id, kind = kind, size = size) },
                modifier = Modifier.size(size),
            )
        }
    }
}
