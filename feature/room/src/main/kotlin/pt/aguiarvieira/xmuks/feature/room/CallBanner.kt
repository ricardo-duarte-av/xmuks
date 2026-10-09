package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pt.aguiarvieira.xmuks.core.data.calls.RoomCall
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/**
 * The header's call action while a call is on: one tonal icon (the banner below says what the call
 * is and offers the choices), so the room's name keeps its room.
 */
@Composable
internal fun CallPill(
    call: RoomCall,
    onCall: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = { onCall(call.isVideo) }, modifier = modifier) {
        Icon(
            painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_call),
            contentDescription =
                if (call.joinedHere) {
                    stringResource(R.string.call_return)
                } else {
                    stringResource(R.string.call_join_count, call.people)
                },
        )
    }
}

/** Starting a call in a group: voice or video, and whether the room is told. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallStartSheet(
    notifyRoom: Boolean,
    onNotifyRoom: (Boolean) -> Unit,
    onCall: (video: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.call_start_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onCall(false) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_call), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.voice_call), Modifier.padding(start = 8.dp))
                }
                Button(onClick = { onCall(true) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_videocam), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.video_call), Modifier.padding(start = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.call_notify_room), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.call_notify_room_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = notifyRoom, onCheckedChange = onNotifyRoom)
            }
        }
    }
}

/** What hangs under the header: the room's call, then its pins. */
@Composable
internal fun UnderHeader(
    call: RoomCall?,
    onCall: ((video: Boolean) -> Unit)?,
    pinned: Int,
    onOpenPins: () -> Unit,
) {
    if (call != null && onCall != null) CallBanner(call, onCall)
    if (pinned > 0) PinnedBar(pinned, onOpenPins)
}

/** Under the header while the room has a call: what it is, how long it has run, and ways in. */
@Composable
internal fun CallBanner(
    call: RoomCall,
    onCall: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    ScreenCard(modifier.padding(horizontal = ScreenCards.Gap).padding(bottom = ScreenCards.Gap)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painterResource(if (call.isVideo) R.drawable.ic_videocam else R.drawable.ic_call),
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (call.isVideo) R.string.call_video_ongoing else R.string.call_voice_ongoing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pluralStringResource(R.plurals.call_people, call.people, call.people) + " · " +
                        elapsed(now - call.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (call.joinedHere) {
                FilledTonalButton(onClick = { onCall(call.isVideo) }) { Text(stringResource(R.string.call_return)) }
            } else {
                OutlinedButton(onClick = { onCall(false) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(painterResource(R.drawable.ic_call), stringResource(R.string.voice_call), Modifier.size(18.dp))
                }
                FilledTonalButton(onClick = { onCall(true) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_videocam),
                        stringResource(R.string.video_call),
                        Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val minutes = (ms / MS_PER_MINUTE).coerceAtLeast(0)
    return if (minutes <
        MINUTES_PER_HOUR
    ) {
        "${minutes}m"
    } else {
        "${minutes / MINUTES_PER_HOUR}h ${minutes % MINUTES_PER_HOUR}m"
    }
}

private const val TICK_MS = 30_000L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
