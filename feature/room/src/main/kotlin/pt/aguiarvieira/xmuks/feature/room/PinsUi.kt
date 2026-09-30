package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Pins
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** What the room screen needs of pins: which, the list once loaded, and changing them. */
@Immutable
class PinsUi(
    val pins: Pins = Pins(),
    val items: List<TimelineItem>? = null,
    val onToggle: (eventId: String) -> Unit = {},
    val onLoad: () -> Unit = {},
)

/** Under the header when anything's pinned: how many, opening the list. */
@Composable
internal fun PinnedBar(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenCard(modifier.padding(horizontal = ScreenCards.Gap).padding(bottom = ScreenCards.Gap)) {
        Row(
            Modifier.fillMaxWidth().tapOrHold(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_pin),
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                pluralStringResource(R.plurals.pinned_count, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The pinned messages, newest pin first; tapping one shows it in the timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinnedSheet(
    pins: PinsUi,
    resolver: MediaResolver,
    onShow: (eventId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val load by rememberUpdatedState(pins.onLoad)
    LaunchedEffect(pins.pins.eventIds) { load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.pinned_messages),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val items = pins.items?.filterIsInstance<TimelineItem.Message>()
        if (items == null) {
            Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(items, key = { it.eventId }) { message ->
                    ListItem(
                        leadingContent = {
                            RoomAvatar(
                                message.senderName,
                                message.sender,
                                resolver.avatar(message.senderAvatarMxc),
                                size = 36.dp
                            )
                        },
                        supportingContent = {
                            Text(summaryOf(message.content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent =
                            if (pins.pins.canPin) {
                                { UnpinButton { pins.onToggle(message.eventId) } }
                            } else {
                                null
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.tapOrHold { onShow(message.eventId) },
                    ) { Text(message.senderName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun UnpinButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_pin_off), stringResource(R.string.unpin))
    }
}
