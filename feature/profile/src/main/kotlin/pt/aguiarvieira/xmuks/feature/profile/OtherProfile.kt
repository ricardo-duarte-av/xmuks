package pt.aguiarvieira.xmuks.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard

/** Only on someone else's profile: our DM with them, ignoring them, the rooms we share. */
@Immutable
class OtherProfile(
    val hasDirectRoom: Boolean,
    val ignored: Boolean,
    /** Null while unknown, or when their server can't tell (MSC2666). */
    val mutualRooms: List<RoomSummary>?,
    val onMessage: () -> Unit,
    val onSetIgnored: (Boolean) -> Unit,
    val onOpenRoom: (roomId: String) -> Unit,
    /** Their xmuks contact in the phone's Contacts, when there is one. */
    val phoneContact: PhoneContactLink? = null,
    /** Link their contact to a phone contact picked (a contact URI), or unlink it (null). */
    val onLinkPhoneContact: (Uri?) -> Unit = {},
)

/** Go to (or start) our DM with them; ignore them (after asking) or stop ignoring them. */
@Composable
internal fun ContactCard(
    other: OtherProfile,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        ScreenCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = other.onMessage, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(18.dp))
                    Text(
                        stringResource(if (other.hasDirectRoom) R.string.go_to_dm else R.string.start_dm),
                        Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { if (other.ignored) other.onSetIgnored(false) else confirming = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_block), null, Modifier.size(18.dp))
                    Text(
                        stringResource(if (other.ignored) R.string.unignore else R.string.ignore),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        other.phoneContact?.let { contact ->
            val pick =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.PickContact()
                ) { it?.let(other.onLinkPhoneContact) }
            ScreenCard(Modifier.padding(top = 8.dp)) {
                TextButton(
                    onClick = { if (contact.linked) other.onLinkPhoneContact(null) else pick.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                ) {
                    val label = if (contact.linked) R.string.unlink_phone_contact else R.string.link_phone_contact
                    Text(stringResource(label))
                }
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            R.string.ignore,
            R.string.ignore_confirm,
            { other.onSetIgnored(true) },
            { confirming = false },
        )
    }
}

/** The rooms we share: the most recently active few, and all of them in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MutualRoomsCard(
    rooms: List<RoomSummary>,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var all by rememberSaveable { mutableStateOf(false) }
    ScreenCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.mutual_rooms, rooms.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (rooms.isEmpty()) {
                Text(
                    stringResource(R.string.mutual_rooms_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            rooms.take(PREVIEWED).forEach { RoomRow(it, onOpenRoom) }
            if (rooms.size > PREVIEWED) {
                TextButton(onClick = { all = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.mutual_rooms_all, rooms.size))
                }
            }
        }
    }
    if (all) {
        ModalBottomSheet(onDismissRequest = { all = false }) {
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(rooms, key = { it.roomId }) { room ->
                    RoomRow(room) {
                        all = false
                        onOpenRoom(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(
    room: RoomSummary,
    onOpen: (String) -> Unit,
) {
    ListItem(
        leadingContent = { RoomAvatar(room.name, room.roomId, room.avatarUrl, size = 40.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpen(room.roomId) },
    ) { Text(room.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** On our own profile: the ways to our preferences and to everyone we ignore. */
@Composable
internal fun OwnLinksCard(
    onOpenPreferences: () -> Unit,
    onOpenIgnoredUsers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenCard(modifier) {
        Column {
            LinkRow(R.drawable.ic_tune, R.string.preferences, onOpenPreferences)
            LinkRow(R.drawable.ic_block, R.string.ignored_users, onOpenIgnoredUsers)
        }
    }
}

@Composable
internal fun LinkRow(
    icon: Int,
    title: Int,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(title)) }
}

private const val PREVIEWED = 5
