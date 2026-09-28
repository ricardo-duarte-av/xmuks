package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Avatar + name in an app bar: the landing spot of the list's shared elements. */
@Composable
fun HeaderTitle(
    id: String,
    name: String,
    avatarUrl: String?,
    sharedScope: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(
            name = name,
            id = id,
            avatarUrl = avatarUrl,
            size = 40.dp,
            modifier =
                Modifier
                    .sharedElement(SharedKeys.avatar(id, sharedScope))
                    .then(
                        if (onAvatarClick !=
                            null
                        ) {
                            Modifier.clip(CircleShape).clickable(onClick = onAvatarClick)
                        } else {
                            Modifier
                        }
                    ),
        )
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedElement(SharedKeys.title(id, sharedScope)),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
