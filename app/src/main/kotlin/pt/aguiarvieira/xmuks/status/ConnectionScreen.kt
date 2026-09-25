package pt.aguiarvieira.xmuks.status

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.connection.SyncSummary
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import java.text.DateFormat
import java.util.Date

/** M1 home: proves login → `/sse` → decode end to end. Replaced by the room list in M3. */
@Composable
fun ConnectionRoute(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    ConnectionScreen(state, summary, viewModel.server, viewModel::reconnect, viewModel::logout, modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionScreen(
    state: ConnectionState,
    summary: SyncSummary,
    server: String,
    onReconnect: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("xmuks") },
                subtitle = { Text(summary.userId ?: server) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Stat("Rooms", summary.rooms.toString())
                Stat("Spaces", summary.spaces.toString())
                Stat("Top-level", summary.topLevelSpaces.toString())
                Stat("DMs", summary.dms.toString())
                Stat(
                    if (summary.catchup) "Catch-up" else "Initial sync",
                    summary.initialSyncMs?.let { "$it ms" } ?: "…"
                )
                Stat("Live events", summary.liveEvents.toString())
            }
            if (summary.lastServerTs > 0) {
                Text(
                    "Last sync: " + DateFormat.getDateTimeInstance().format(Date(summary.lastServerTs)) +
                        (summary.homeserverSync?.let { " · homeserver: $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state == ConnectionState.AuthFailed) {
                    Button(onClick = onLogout) { Text("Sign in again") }
                } else {
                    Button(onClick = onReconnect) { Text("Reconnect") }
                    OutlinedButton(onClick = onLogout) { Text("Log out") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(state: ConnectionState) {
    val colors = MaterialTheme.colorScheme
    val (label, tone) =
        when (state) {
            ConnectionState.Idle -> {
                "Paused (app in background)" to colors.outline
            }

            is ConnectionState.Connecting -> {
                "Connecting…" to colors.tertiary
            }

            is ConnectionState.Initializing -> {
                (if (state.catchup) "Catching up" else "Syncing") +
                    " · ${state.rooms} rooms" to
                    colors.tertiary
            }

            ConnectionState.Live -> {
                "Live" to colors.primary
            }

            is ConnectionState.Retrying -> {
                "Retrying in ${state.inMs / 1000}s · ${state.error}" to colors.error
            }

            ConnectionState.AuthFailed -> {
                "Password rejected" to colors.error
            }
        }
    val dot by animateColorAsState(tone, label = "status")
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state is ConnectionState.Connecting || state is ConnectionState.Initializing) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            } else {
                Box(Modifier.size(16.dp).background(dot, CircleShape))
            }
            Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.width(104.dp).padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
