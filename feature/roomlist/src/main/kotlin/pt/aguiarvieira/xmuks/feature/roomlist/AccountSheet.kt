package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.AvatarKind
import pt.aguiarvieira.xmuks.core.designsystem.component.InitialsAvatar
import pt.aguiarvieira.xmuks.core.network.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    account: String,
    connection: ConnectionState,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InitialsAvatar(name = account, id = account, kind = AvatarKind.Person)
                Column {
                    Text(account, style = MaterialTheme.typography.titleMedium)
                    Text(
                        statusText(connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                val label = if (connection == ConnectionState.AuthFailed) R.string.sign_in_again else R.string.log_out
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun statusText(state: ConnectionState): String =
    stringResource(
        when (state) {
            ConnectionState.Live -> {
                R.string.status_live
            }

            ConnectionState.Idle -> {
                R.string.status_paused
            }

            is ConnectionState.Connecting -> {
                R.string.status_connecting
            }

            is ConnectionState.Initializing -> {
                if (state.catchup) R.string.status_catching_up else R.string.status_syncing
            }

            is ConnectionState.Retrying -> {
                R.string.status_offline
            }

            ConnectionState.AuthFailed -> {
                R.string.status_auth_failed
            }
        },
    )
