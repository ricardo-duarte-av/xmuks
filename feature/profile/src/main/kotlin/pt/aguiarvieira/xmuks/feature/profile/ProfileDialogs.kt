package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import java.time.ZoneId

/** Save, and (when there's something to take away) Clear, which saves an empty value. */
@Composable
private fun EditDialog(
    title: String,
    onSave: () -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            TextButton(onClick = {
                onSave()
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text(stringResource(R.string.clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** One piece of text: a name, or the biography's markdown. */
@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    singleLine: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    val text = rememberTextFieldState(initial)
    EditDialog(title, onSave = { onSave(text.text.toString()) }, onClear = null, onDismiss = onDismiss) {
        OutlinedTextField(
            state = text,
            placeholder = hint?.let { { Text(it) } },
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(3, 10),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** MSC4426: an emoji and a line of text (at most 32 and 256 bytes; characters here, close enough). */
@Composable
internal fun StatusDialog(
    current: UserProfile.Status?,
    onSave: (text: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val emoji = rememberTextFieldState(current?.emoji.orEmpty())
    val text = rememberTextFieldState(current?.text.orEmpty())
    EditDialog(
        stringResource(R.string.status),
        onSave = { onSave(text.text.toString(), emoji.text.toString()) },
        onClear = current?.let { { onSave("", "") } },
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = emoji,
                label = { Text(stringResource(R.string.status_emoji)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLengthTrim(STATUS_EMOJI_MAX),
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                state = text,
                label = { Text(stringResource(R.string.status_text)) },
                lineLimits = TextFieldLineLimits.MultiLine(1, 3),
                inputTransformation = InputTransformation.maxLengthTrim(STATUS_TEXT_MAX),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Free text (several sets separated by commas), with the common English sets a tap away. */
@Composable
internal fun PronounsDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = rememberTextFieldState(current)
    EditDialog(
        stringResource(R.string.pronouns),
        onSave = { onSave(text.text.toString()) },
        onClear = current.takeIf { it.isNotEmpty() }?.let { { onSave("") } },
        onDismiss = onDismiss,
    ) {
        Column {
            OutlinedTextField(
                state = text,
                placeholder = { Text(stringResource(R.string.pronouns_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                COMMON_PRONOUNS.forEach { set ->
                    SuggestionChip(onClick = { text.setTextAndPlaceCursorAtEnd(set) }, label = { Text(set) })
                }
            }
        }
    }
}

/** Every IANA zone, searchable, with the device's own on top. Picking one saves it. */
@Composable
internal fun TimezoneDialog(
    current: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val query = rememberTextFieldState()
    val zones = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc/") }.sorted() }
    val shown by remember {
        derivedStateOf {
            val q =
                query.text
                    .toString()
                    .trim()
                    .replace(' ', '_')
            if (q.isEmpty()) zones else zones.filter { it.contains(q, ignoreCase = true) }
        }
    }
    val device = remember { ZoneId.systemDefault().id }
    val pick = { zone: String? ->
        onSave(zone)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timezone)) },
        text = {
            Column {
                OutlinedTextField(
                    state = query,
                    placeholder = { Text(stringResource(R.string.timezone_search)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { pick(device) }) { Text(stringResource(R.string.timezone_device, device)) }
                LazyColumn(Modifier.heightIn(max = ZONE_LIST_HEIGHT)) {
                    items(shown, key = { it }) { zone ->
                        Text(
                            zone,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (zone ==
                                    current
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            modifier = Modifier.fillMaxWidth().clickable { pick(zone) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = {
            if (current != null) TextButton(onClick = { pick(null) }) { Text(stringResource(R.string.clear)) }
        },
    )
}

private val COMMON_PRONOUNS = listOf("they/them", "she/her", "he/him", "it/its")
private const val STATUS_EMOJI_MAX = 16
private const val STATUS_TEXT_MAX = 256
private val ZONE_LIST_HEIGHT = 320.dp
