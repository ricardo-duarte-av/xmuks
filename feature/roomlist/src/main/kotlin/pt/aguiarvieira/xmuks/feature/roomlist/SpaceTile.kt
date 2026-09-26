package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.UnreadBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement

/** A space in the Spaces grid: large cookie avatar (it morphs into the space screen's header). */
@Composable
fun SpaceTile(
    space: SpaceSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            RoomAvatar(
                name = space.name,
                id = space.roomId,
                avatarUrl = space.avatarUrl,
                size = 84.dp,
                modifier = Modifier.sharedElement(SharedKeys.avatar(space.roomId, SharedScopes.SPACES)),
            )
            val (level, count) = space.unread.level()
            UnreadBadge(level, count, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        }
        Text(
            text = space.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (space.unread.any) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(
                        horizontal = 4.dp
                    ).sharedElement(SharedKeys.title(space.roomId, SharedScopes.SPACES)),
        )
    }
}
