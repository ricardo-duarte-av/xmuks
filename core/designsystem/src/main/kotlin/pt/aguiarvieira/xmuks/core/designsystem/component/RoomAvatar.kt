package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

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
    // The image simply paints over the initials once loaded (nothing painted on failure), which
    // avoids a subcomposition per row — expensive in lazy lists.
    Box(modifier = modifier.size(size).clip(shape)) {
        InitialsAvatar(name = name, id = id, kind = kind, size = size)
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
