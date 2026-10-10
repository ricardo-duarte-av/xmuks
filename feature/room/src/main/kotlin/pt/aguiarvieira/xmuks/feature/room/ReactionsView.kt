package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.ReactionGroup
import pt.aguiarvieira.xmuks.core.data.timeline.Reactor
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/** A message's reactions being looked at: who reacted with what. */
@Immutable
data class ReactionsView(
    val eventId: String,
    /** The reaction held to open this: shown first. */
    val first: String?,
    /** Null while loading; empty when gomuks has none (or couldn't say). */
    val groups: List<ReactionGroup>?,
)

/** Each reaction to a message, with everyone who reacted with it under it; tapping someone opens them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReactionsSheet(
    view: ReactionsView,
    resolver: MediaResolver,
    onOpenUser: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Off to someone's profile: the sheet steps aside, and is back when the room is.
    var away by remember(view) { mutableStateOf(false) }
    if (away) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.reactions_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
        )
        val groups = view.groups?.let { all -> all.sortedByDescending { it.key == view.first } }
        when {
            groups == null -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }

            groups.isEmpty() -> {
                Text(
                    stringResource(R.string.reactions_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                )
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    groups.forEachIndexed { index, group ->
                        item(key = "key:${group.key}") {
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                            GroupHeader(group, resolver)
                        }
                        items(group.reactors, key = { "${group.key}:${it.userId}" }) { reactor ->
                            ReactorRow(reactor, resolver) {
                                away = true
                                onOpenUser(reactor.userId)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The reaction itself, big, with its shortcode (custom emoji) and how many chose it. */
@Composable
private fun GroupHeader(
    group: ReactionGroup,
    resolver: MediaResolver,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (group.isImage) {
            AsyncImage(
                model = resolver.media(group.key, false),
                contentDescription = group.shortcode,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Text(group.key.take(MAX_KEY_CHARS), style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            listOfNotNull(
                group.shortcode?.let { ":$it:" },
                pluralStringResource(R.plurals.reactions_count, group.reactors.size, group.reactors.size),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReactorRow(
    reactor: Reactor,
    resolver: MediaResolver,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoomAvatar(reactor.name, reactor.userId, resolver.avatar(reactor.avatarMxc), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                reactor.name,
                style = MaterialTheme.typography.titleSmall,
                color = senderColor(reactor.userId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reactor.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val MAX_KEY_CHARS = 24
