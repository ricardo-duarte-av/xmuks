package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.network.ConnectionState

/** A slim strip under the app bar while the stream isn't live; invisible when all is well. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val label =
        when (state) {
            ConnectionState.Live, ConnectionState.Idle -> {
                null
            }

            is ConnectionState.Connecting -> {
                stringResource(R.string.status_connecting)
            }

            is ConnectionState.Initializing -> {
                stringResource(if (state.catchup) R.string.status_catching_up else R.string.status_syncing)
            }

            is ConnectionState.Retrying -> {
                stringResource(R.string.status_offline)
            }

            ConnectionState.AuthFailed -> {
                stringResource(R.string.status_auth_failed)
            }
        }
    AnimatedVisibility(
        visible = label != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        val error = state is ConnectionState.Retrying || state == ConnectionState.AuthFailed
        Surface(
            color = with(MaterialTheme.colorScheme) { if (error) errorContainer else secondaryContainer },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!error) LoadingIndicator(modifier = Modifier.size(24.dp))
                Text(label.orEmpty(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
