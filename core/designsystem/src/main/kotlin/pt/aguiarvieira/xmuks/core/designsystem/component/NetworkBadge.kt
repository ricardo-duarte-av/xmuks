package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bridged room's network (its logo, or its initial), ringed in the surface colour so it reads
 * over the room's avatar: the room list's badge and the room header's.
 */
@Composable
fun NetworkBadge(
    protocol: String,
    logoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(RING),
        contentAlignment = Alignment.Center,
    ) {
        RoomAvatar(protocol, protocol, logoUrl, size = size - RING * 2)
    }
}

private val RING = 2.dp
