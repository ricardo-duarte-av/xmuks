package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications

/** How this room notifies: the account's default, every message, mentions and keywords, or nothing. */
@Composable
internal fun RoomNotificationsDialog(
    current: RoomNotifications,
    onChoose: (RoomNotifications) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_notifications)) },
        text = {
            Column {
                RoomNotifications.entries.forEach { option ->
                    val (title, detail) = texts(option)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChoose(option)
                                    onDismiss()
                                }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Column {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun texts(option: RoomNotifications): Pair<Int, Int> =
    when (option) {
        RoomNotifications.Default -> R.string.notify_default to R.string.notify_default_detail
        RoomNotifications.All -> R.string.notify_all to R.string.notify_all_detail
        RoomNotifications.MentionsAndKeywords -> R.string.notify_mentions to R.string.notify_mentions_detail
        RoomNotifications.Off -> R.string.notify_off to R.string.notify_off_detail
    }
