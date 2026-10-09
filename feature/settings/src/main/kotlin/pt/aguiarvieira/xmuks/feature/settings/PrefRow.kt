package pt.aguiarvieira.xmuks.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pt.aguiarvieira.xmuks.core.data.prefs.Pref
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope

/**
 * One preference: what it does, its value (a switch, or the chosen value), where that value comes
 * from, and — when it's set in this scope — the way to clear it.
 */
@Composable
internal fun PrefRow(
    entry: PrefEntry,
    layers: PrefLayers,
    scope: PrefScope,
    edits: PrefEdits,
) {
    val pref = entry.pref
    val setHere = layers.lookup(pref, scope) != null
    var editing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Turning on a preference that needs permissions asks for them first; it's only set if granted.
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) (pref as? Pref.Bool)?.let { edits.setBool(it, scope, true) }
        }
    val setBool: (Pref.Bool, Boolean) -> Unit = { bool, on ->
        val missing =
            entry.permissions.filter {
                ContextCompat.checkSelfPermission(context, it) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (on && missing.isNotEmpty()) ask.launch(missing.toTypedArray()) else edits.setBool(bool, scope, on)
    }
    val toggle: (() -> Unit)? =
        (pref as? Pref.Bool)?.let { bool -> { setBool(bool, !layers.get(bool)) } }
    ListItem(
        supportingContent = { Details(entry, layers, scope, setHere) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (setHere) {
                    IconButton(onClick = { edits.clear(pref, scope) }) {
                        Icon(painterResource(R.drawable.ic_reset), stringResource(R.string.reset))
                    }
                }
                when (pref) {
                    is Pref.Bool -> Switch(layers.get(pref), onCheckedChange = { setBool(pref, it) })
                    is Pref.Number -> Text(layers.get(pref).toString(), style = MaterialTheme.typography.labelLarge)
                    is Pref.Choice -> Text(choiceLabel(layers.get(pref)), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.tappable(toggle ?: { editing = true }),
    ) { Text(stringResource(entry.title)) }
    if (editing) {
        val close = { editing = false }
        (pref as? Pref.Number)?.let {
            NumberDialog(
                entry.title,
                layers.get(it),
                { v -> edits.setNumber(it, scope, v) },
                close
            )
        }
        (pref as? Pref.Choice)?.let {
            ChoiceDialog(entry.title, it.values, layers.get(it), { v -> edits.setChoice(it, scope, v) }, close)
        }
    }
}

@Composable
private fun Details(
    entry: PrefEntry,
    layers: PrefLayers,
    scope: PrefScope,
    setHere: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(entry.description))
        val source = layers.source(entry.pref)
        val status =
            when {
                setHere -> stringResource(R.string.set_here)
                source != null -> stringResource(R.string.from_scope, stringResource(scopeName(source)))
                else -> stringResource(R.string.from_default)
            }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (setHere ||
                    source == scope
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        if (!entry.inXmuks) {
            Text(
                stringResource(R.string.not_in_xmuks),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun choiceLabel(value: String?): String = CHOICE_LABELS[value]?.let { stringResource(it) } ?: value.orEmpty()

@Composable
private fun ChoiceDialog(
    title: Int,
    values: List<String?>,
    current: String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            // Code themes run to 65: a list that scrolls.
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(values) { value ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChoose(value)
                                onDismiss()
                            }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = null)
                        Text(choiceLabel(value), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberDialog(
    title: Int,
    current: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = rememberTextFieldState(current.toString())
    val number =
        text.text
            .toString()
            .toIntOrNull()
            ?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                state = text,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = number != null, onClick = {
                number?.let(onSave)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
