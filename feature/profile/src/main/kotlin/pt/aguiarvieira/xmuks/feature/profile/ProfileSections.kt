package pt.aguiarvieira.xmuks.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.richtext.HtmlContent
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * An avatar or banner. Tapping opens it in the viewer; on our own profile it offers to view,
 * change (the photo picker) or remove it instead.
 */
@Composable
internal fun ImageSlot(
    mxc: String?,
    title: String,
    viewer: () -> ViewerMedia?,
    onOpenMedia: (ViewerMedia) -> Unit,
    onChange: ((Uri?) -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onChange?.invoke(uri)
        }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Box(
        modifier.clickable {
            when {
                onChange != null && mxc == null -> pick()
                onChange != null -> menu = true
                else -> viewer()?.let(onOpenMedia)
            }
        },
    ) {
        content()
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.view)) }, onClick = {
                menu = false
                viewer()?.let(onOpenMedia)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.change)) }, onClick = {
                menu = false
                pick()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = {
                menu = false
                onChange?.invoke(null)
            })
        }
    }
}

private enum class Detail { Status, Pronouns, Timezone }

/** Status, pronouns and local time: shown when set, and always (to set them) on our own profile. */
@Composable
internal fun DetailsCard(
    profile: UserProfile,
    edits: ProfileEdits?,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf<Detail?>(null) }
    val status = profile.status?.let { listOfNotNull(it.emoji, it.text.takeIf(String::isNotBlank)).joinToString(" ") }
    val pronouns = profile.pronouns.joinToString(", ") { it.summary }.takeIf { it.isNotEmpty() }
    val time = profile.timezone?.let { localTime(it) }
    if (edits == null && listOf(status, pronouns, time).all { it == null }) return
    ScreenCard(modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            DetailRow(R.string.status, status, edits?.let { { editing = Detail.Status } })
            DetailRow(R.string.pronouns, pronouns, edits?.let { { editing = Detail.Pronouns } })
            DetailRow(R.string.timezone, time, edits?.let { { editing = Detail.Timezone } })
        }
    }
    if (edits == null) return
    val close = { editing = null }
    when (editing) {
        Detail.Status -> StatusDialog(profile.status, edits.setStatus, close)
        Detail.Pronouns -> PronounsDialog(pronouns.orEmpty(), edits.setPronouns, close)
        Detail.Timezone -> TimezoneDialog(profile.timezone, edits.setTimezone, close)
        null -> Unit
    }
}

/** One labelled value; hidden when unset unless it can be edited. */
@Composable
private fun DetailRow(
    label: Int,
    value: String?,
    onEdit: (() -> Unit)?,
) {
    if (value == null && onEdit == null) return
    ListItem(
        overlineContent = { Text(stringResource(label)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier,
    ) {
        Text(
            value ?: stringResource(R.string.not_set),
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
    }
}

/** The time where they are, e.g. "14:32 (Europe/Lisbon)", ticking once a minute. */
@Composable
private fun localTime(zone: String): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS - now % MINUTE_MS)
            now = System.currentTimeMillis()
        }
    }
    val time =
        remember(zone, now / MINUTE_MS) {
            try {
                val there = Instant.ofEpochMilli(now).atZone(ZoneId.of(zone))
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(there)
            } catch (_: DateTimeException) {
                null
            }
        }
    return if (time == null) zone else stringResource(R.string.local_time, time, zone)
}

/** The biography (MSC4440), rendered like a message. */
@Composable
internal fun AboutCard(
    profile: UserProfile,
    media: ProfileMedia,
    edits: ProfileEdits?,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bio = profile.bio
    if (bio == null && edits == null) return
    var editing by rememberSaveable { mutableStateOf(false) }
    ScreenCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.about),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                if (edits != null) {
                    IconButton(onClick = { editing = true }) {
                        Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit))
                    }
                }
            }
            if (bio != null) {
                HtmlContent(
                    bio.html,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.typography.bodyLarge,
                    mediaUrl = { media.full(it) },
                    modifier = Modifier.padding(end = 12.dp),
                    onOpenImage = { mxc, alt -> media.viewer(mxc, alt)?.let(onOpenMedia) },
                )
            } else {
                Text(stringResource(R.string.bio_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editing && edits != null) {
        TextDialog(
            title = stringResource(R.string.about),
            initial = bio?.editSource.orEmpty(),
            singleLine = false,
            hint = stringResource(R.string.bio_hint),
            onSave = edits.setBio,
            onDismiss = { editing = false },
        )
    }
}

/** Which account this is, how the connection is doing, and logging out. */
@Composable
internal fun AccountCard(
    account: String,
    connection: ConnectionState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.account), style = MaterialTheme.typography.titleMedium)
            Text(account, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                statusText(connection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
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

private const val MINUTE_MS = 60_000L
