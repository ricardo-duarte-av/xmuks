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
 * A bridged network (its logo, or its initial) as it is, transparency and all. Over another avatar
 * (the room list's, a sender's) it's [ringed] in the surface colour so it reads; on its own (the
 * room header's) it isn't.
 */
@Composable
fun NetworkBadge(
    protocol: String,
    logoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    ringed: Boolean = true,
) {
    Box(
        if (ringed) modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(RING) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        RoomAvatar(protocol, protocol, logoUrl, size = if (ringed) size - RING * 2 else size, backdrop = false)
    }
}

private val RING = 2.dp
