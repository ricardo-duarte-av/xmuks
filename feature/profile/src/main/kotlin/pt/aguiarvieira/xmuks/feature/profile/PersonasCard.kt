package pt.aguiarvieira.xmuks.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import java.util.UUID

/**
 * Our per-message profiles (MSC4461), global or one room's: each with its triggers, the default
 * marked. Tap to edit.
 */
@Composable
internal fun PersonasCard(
    personas: PerMessageProfiles,
    media: ProfileMedia,
    edits: PersonaEdits,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.pmp_title),
    explainer: String = stringResource(R.string.pmp_explainer),
) {
    // The one being edited; a fresh one (not yet in the list) while adding.
    var editing by remember { mutableStateOf<PerMessageProfile?>(null) }
    ScreenCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                explainer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            personas.profiles.forEach { persona ->
                PersonaRow(persona, persona.id == personas.defaultId, media) { editing = persona }
            }
            TextButton(
                onClick = { editing = PerMessageProfile(UUID.randomUUID().toString(), null, null, emptyList()) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), null)
                Text(stringResource(R.string.pmp_add), Modifier.padding(start = 8.dp))
            }
        }
    }
    editing?.let { persona ->
        val exists = personas.profiles.any { it.id == persona.id }
        PersonaEditor(
            persona = persona,
            isDefault = persona.id == personas.defaultId,
            media = media,
            onUploadAvatar = edits.uploadAvatar,
            onSave = edits.save,
            onDelete = if (exists) ({ edits.delete(persona.id) }) else null,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PersonaRow(
    persona: PerMessageProfile,
    isDefault: Boolean,
    media: ProfileMedia,
    onClick: () -> Unit,
) {
    val name = persona.displayName ?: persona.id
    val triggers =
        persona.triggers.joinToString("   ") { it.label }.ifEmpty { stringResource(R.string.pmp_no_triggers) }
    ListItem(
        leadingContent = { RoomAvatar(name, persona.id, media.thumbnail(persona.avatarMxc), size = 40.dp) },
        supportingContent = { Text(triggers, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent =
            if (isDefault) {
                { Text(stringResource(R.string.pmp_default), color = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** A trigger being edited. */
private class TriggerDraft(
    prefix: String,
    suffix: String,
    keep: Boolean,
) {
    val prefix = TextFieldState(prefix)
    val suffix = TextFieldState(suffix)
    var keep by mutableStateOf(keep)

    fun toTrigger() = PerMessageProfile.Trigger(prefix.text.toString(), suffix.text.toString(), keep)
}

/** Name, avatar, whether it's the default, and its triggers. */
@Composable
private fun PersonaEditor(
    persona: PerMessageProfile,
    isDefault: Boolean,
    media: ProfileMedia,
    onUploadAvatar: (android.net.Uri, (String) -> Unit) -> Unit,
    onSave: (PerMessageProfile, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(persona.displayName.orEmpty())
    var avatar by remember { mutableStateOf(persona.avatarMxc) }
    var makeDefault by remember { mutableStateOf(isDefault) }
    val triggers =
        remember {
            mutableStateListOf(
                *persona.triggers.map { TriggerDraft(it.prefix, it.suffix, it.keepTrigger) }.toTypedArray()
            )
        }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onUploadAvatar(uri) { avatar = it }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(persona.displayName ?: stringResource(R.string.pmp_new)) },
        text = {
            Column(Modifier.heightIn(max = EDITOR_HEIGHT).verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoomAvatar(
                        name.text.toString().ifBlank { "?" },
                        persona.id,
                        media.thumbnail(avatar),
                        size = 56.dp,
                        modifier =
                            Modifier.clip(CircleShape).clickable {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                    )
                    OutlinedTextField(
                        state = name,
                        label = { Text(stringResource(R.string.display_name)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (avatar != null) {
                    TextButton(onClick = { avatar = null }) { Text(stringResource(R.string.remove)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.pmp_use_default), Modifier.weight(1f))
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                }
                Text(stringResource(R.string.pmp_triggers), style = MaterialTheme.typography.titleSmall)
                triggers.forEach { draft -> TriggerFields(draft) { triggers.remove(draft) } }
                TextButton(onClick = { triggers += TriggerDraft("", "", false) }) {
                    Text(stringResource(R.string.pmp_add_trigger))
                }
                if (onDelete != null) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text(stringResource(R.string.pmp_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val saved =
                    persona.copy(
                        displayName =
                            name.text
                                .toString()
                                .trim()
                                .ifEmpty { null },
                        avatarMxc = avatar,
                        triggers =
                            triggers.map { it.toTrigger() }.filter {
                                it.prefix.isNotEmpty() ||
                                    it.suffix.isNotEmpty()
                            },
                    )
                onSave(saved, makeDefault)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TriggerFields(
    draft: TriggerDraft,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = draft.prefix,
                label = { Text(stringResource(R.string.pmp_trigger_prefix)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state = draft.suffix,
                label = { Text(stringResource(R.string.pmp_trigger_suffix)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.keep, onCheckedChange = { draft.keep = it })
            Text(stringResource(R.string.pmp_trigger_keep), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val EDITOR_HEIGHT = 480.dp
