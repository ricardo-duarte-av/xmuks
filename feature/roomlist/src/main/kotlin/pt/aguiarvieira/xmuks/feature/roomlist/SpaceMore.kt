package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryRoom
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryUnsupported
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceHierarchyPage
import pt.aguiarvieira.xmuks.core.data.roominfo.SpaceLink
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** A child of the space we're not in: tapping it previews it, to join from there. */
data class SpaceMoreRoom(
    val room: DirectoryRoom,
    /** The servers to look it up and join it through. */
    val via: List<String>,
    val suggested: Boolean,
)

/** The space screen's "More in this space": what's loaded so far, and whether there's more. */
data class SpaceMoreState(
    val rooms: List<SpaceMoreRoom> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
) {
    /** Shown once there's something to show: rooms, or why they didn't come. */
    val visible: Boolean get() = rooms.isNotEmpty() || error != null
}

/** The hierarchy of one (sub)space as it loads, a page at a time. */
internal data class HierarchyLoad(
    val targetId: String? = null,
    val rooms: List<DirectoryRoom> = emptyList(),
    val links: Map<String, SpaceLink> = emptyMap(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun added(page: SpaceHierarchyPage) =
        copy(
            rooms = (rooms + page.rooms).distinctBy { it.roomId },
            links = links + page.links,
            next = page.nextBatch,
            loading = false,
            error = null,
        )

    /** An older gomuks has no hierarchy: the section just isn't there. */
    fun failed(error: Throwable) =
        if (error is DirectoryUnsupported) {
            copy(loading = false, next = null)
        } else {
            copy(loading = false, error = error.message ?: error.javaClass.simpleName)
        }

    /** Those we're not in ([joined] aside), suggested ones first, else in the space's order. */
    fun shown(joined: Set<String>): SpaceMoreState =
        SpaceMoreState(
            rooms =
                rooms
                    .filter { it.roomId !in joined }
                    .map { room ->
                        val link = links[room.roomId]
                        SpaceMoreRoom(room, link?.via.orEmpty(), link?.suggested == true)
                    }.sortedByDescending { it.suggested },
            loading = loading,
            hasMore = next != null,
            error = error,
        )
}

/** What the section does: open a room's preview, fold, page on, retry. */
class SpaceMoreActions(
    val onOpen: (roomId: String, via: List<String>) -> Unit = { _, _ -> },
    val onToggle: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Below the joined rooms: a foldable heading, then the rest of the space, loaded as it scrolls into view. */
internal fun LazyListScope.spaceMoreSection(
    more: SpaceMoreState,
    expanded: Boolean,
    avatar: (String?) -> String?,
    actions: SpaceMoreActions,
) {
    item(key = "more:heading", contentType = "heading") {
        SpaceMoreHeading(more, expanded, actions.onToggle, Modifier.animateItem())
    }
    if (!expanded) return
    items(more.rooms, key = { "more:" + it.room.roomId }, contentType = { "more" }) { row ->
        SpaceMoreItem(row, avatar, { actions.onOpen(row.room.roomId, row.via) }, Modifier.animateItem())
    }
    when {
        more.error != null -> {
            item(key = "more:error", contentType = "more:status") {
                Column(
                    Modifier.animateItem().fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.space_more_failed, more.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.directory_retry)) }
                }
            }
        }

        more.hasMore -> {
            item(key = "more:next", contentType = "more:status") {
                // In view: the next page.
                LaunchedEffect(more.rooms.size) { actions.onLoadMore() }
                Box(Modifier.animateItem().fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SpaceMoreHeading(
    more: SpaceMoreState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(
        if (expanded) 0f else -90f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fold",
    )
    val count = more.rooms.size.toString() + if (more.hasMore) "+" else ""
    val state = stringResource(if (expanded) R.string.space_more_expanded else R.string.space_more_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.space_more, count),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.rotate(turn),
        )
    }
}

/** A room we're not in: as a joined room's row, with its topic (or address) where the last message goes. */
@Composable
internal fun SpaceMoreItem(
    row: SpaceMoreRoom,
    avatar: (String?) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = row.room
    val colors = MaterialTheme.colorScheme
    val display = LocalRoomListDisplay.current
    val name = room.name ?: room.alias ?: room.roomId
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = display.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomAvatar(name = name, id = room.roomId, avatarUrl = avatar(room.avatarMxc), size = display.avatar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    pluralStringResource(R.plurals.directory_members, room.members, room.members),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            val suggested = stringResource(R.string.space_more_suggested)
            val kind = stringResource(R.string.space_edit_kind_space)
            val rest = listOfNotNull(kind.takeIf { room.isSpace }, room.topic ?: room.alias)
            val line =
                buildAnnotatedString {
                    if (row.suggested) {
                        withStyle(SpanStyle(color = colors.primary)) { append(suggested) }
                        if (rest.isNotEmpty()) append(" · ")
                    }
                    append(rest.joinToString(" · "))
                }
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
