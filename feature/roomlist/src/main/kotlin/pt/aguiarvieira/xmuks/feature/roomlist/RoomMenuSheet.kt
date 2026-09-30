package pt.aguiarvieira.xmuks.feature.roomlist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.rooms.RoomMenuState
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary

/** A room's long-press menu in the room list: its tags and mute as switches, then mark read and a shortcut. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomMenuSheet(
    room: RoomSummary,
    viewModel: RoomMenuViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val flow = remember(room.roomId) { viewModel.state(room.roomId) }
    val state by flow.collectAsStateWithLifecycle(RoomMenuState())
    val refused = stringResource(R.string.room_menu_shortcut_unsupported)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                room.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Toggle(R.drawable.ic_star, R.string.room_menu_favourite, state.favourite) {
                viewModel.setFavourite(room.roomId, it)
            }
            Toggle(R.drawable.ic_low_priority, R.string.room_menu_low_priority, state.lowPriority) {
                viewModel.setLowPriority(room.roomId, it)
            }
            Toggle(R.drawable.ic_notifications_off, R.string.room_menu_mute, state.muted) {
                viewModel.setMuted(room.roomId, it)
            }
            if (room.unread.any) {
                Action(R.drawable.ic_done_all, R.string.room_menu_mark_read) {
                    viewModel.markRead(room.roomId)
                    onDismiss()
                }
            }
            Action(R.drawable.ic_add_home, R.string.room_menu_add_shortcut) {
                viewModel.pinShortcut(room) { Toast.makeText(context, refused, Toast.LENGTH_SHORT).show() }
                onDismiss()
            }
        }
    }
}

@Composable
private fun Toggle(
    icon: Int,
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = ListItem(
    leadingContent = { Icon(painterResource(icon), null) },
    trailingContent = { Switch(checked, onCheckedChange = null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable { onChange(!checked) },
) { Text(stringResource(label)) }

@Composable
private fun Action(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) = ListItem(
    leadingContent = { Icon(painterResource(icon), null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(onClick = onClick),
) { Text(stringResource(label)) }
