package pt.aguiarvieira.xmuks.feature.roomlist

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvent
import pt.aguiarvieira.xmuks.core.designsystem.util.ListTimestamps

/**
 * Events found across rooms, newest (or best) first, loading more as the end comes into view. With
 * [oneRoom] set, all of them are in that room: it heads the list once and each hit leads with its sender.
 */
@Composable
internal fun FoundEventList(
    items: List<FoundEvent>,
    loading: Boolean,
    hasMore: Boolean,
    /** Shown when there's nothing (and nothing loading). */
    emptyText: String,
    now: Long,
    avatar: (String?) -> String?,
    onLoadMore: () -> Unit,
    onOpen: (roomId: String, eventId: String) -> Unit,
    oneRoom: String? = null,
) {
    val list = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            list.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index == list.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore && !loading) loadMore() }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    when {
        items.isEmpty() && loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        items.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            Column(Modifier.fillMaxSize()) {
                if (oneRoom != null) FoundRoomHeader(items.firstOrNull { it.room != null }?.room, oneRoom)
                FoundHits(items, list, loading, now, is24Hour, avatar, showRoom = oneRoom == null, onOpen)
            }
        }
    }
}

@Composable
private fun FoundHits(
    items: List<FoundEvent>,
    list: LazyListState,
    loading: Boolean,
    now: Long,
    is24Hour: Boolean,
    avatar: (String?) -> String?,
    showRoom: Boolean,
    onOpen: (roomId: String, eventId: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items, key = { it.eventId }) { found ->
            val time = ListTimestamps.format(found.timestamp, now, is24Hour = is24Hour)
            FoundEventRow(found, avatar, time, showRoom) { onOpen(found.roomId, found.eventId) }
        }
        if (loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
