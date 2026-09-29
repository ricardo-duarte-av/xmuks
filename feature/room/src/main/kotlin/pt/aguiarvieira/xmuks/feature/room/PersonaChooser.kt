package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * "Sending as …" above the message box, only while the message would go out as a per-message
 * profile (the default, or a trigger in what's typed). ✕ drops the default; a trigger is dropped
 * by editing the text. Tapping it opens the chooser.
 */
@Composable
internal fun SendingAsBanner(
    personas: Personas,
    draft: TextFieldState,
    avatarUrl: (String?) -> String?,
    onChoose: (String?) -> Unit,
    onOpenChooser: () -> Unit,
) {
    val active by remember(personas) { derivedStateOf { personas.active(draft.text.toString()) } }
    val persona = active ?: return
    val name = persona.displayName ?: persona.id
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChooser).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoomAvatar(name, persona.id, avatarUrl(persona.avatarMxc), size = 22.dp)
        Text(
            stringResource(R.string.send_as, name),
            style = MaterialTheme.typography.labelLarge,
            color = senderColor(persona.id),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (persona.id == personas.defaultId) {
            IconButton(onClick = { onChoose(null) }, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.send_as_me))
            }
        }
    }
}

/** Who messages go out as by default here: ourselves, or one of our per-message profiles. */
@Composable
internal fun PersonaChooser(
    personas: Personas,
    avatarUrl: (String?) -> String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = personas.defaultId
    val pick = { id: String? ->
        onChoose(id)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.send_as_title)) },
        text = {
            Column {
                val me = personas.me
                PersonaRow(me?.displayName ?: stringResource(R.string.send_as_me), chosen == null, { pick(null) }) {
                    RoomAvatar(me?.displayName.orEmpty(), me?.userId.orEmpty(), me?.avatarUrl, size = 32.dp)
                }
                personas.choices.forEach { p ->
                    val name = p.displayName ?: p.id
                    PersonaRow(name, chosen == p.id, { pick(p.id) }) {
                        RoomAvatar(name, p.id, avatarUrl(p.avatarMxc), size = 32.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PersonaRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        avatar()
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
