package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels

/** One of [choices] (wire value → label), the [current] one marked; choosing applies it at once. */
@Composable
internal fun ChoiceDialog(
    title: Int,
    choices: Map<String, Int>,
    current: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    RadioRow(stringResource(label), value == current) {
                        if (value != current) onChoose(value)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ConfirmDialog(
    title: Int,
    message: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Confirms something done to someone (or to us: leaving), with an optional reason. */
@Composable
internal fun ReasonDialog(
    title: String,
    onConfirm: (reason: String?) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    val reason = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it) }
                OutlinedTextField(
                    state = reason,
                    label = { Text(stringResource(R.string.member_reason)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    reason.text
                        .toString()
                        .trim()
                        .ifEmpty { null }
                )
                onDismiss()
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A Matrix ID to invite. */
@Composable
internal fun InviteDialog(
    onInvite: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val user = rememberTextFieldState()
    val id = user.text.toString().trim()
    val valid = id.startsWith("@") && id.contains(':') && !id.contains(' ')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invite)) },
        text = {
            OutlinedTextField(
                state = user,
                placeholder = { Text(stringResource(R.string.invite_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onInvite(id)
                onDismiss()
            }) { Text(stringResource(R.string.invite)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * A member's new level: admin, moderator, member, or any number — never above ours. Lowering our
 * own is warned about: it can't be undone by us.
 */
@Composable
internal fun RoleDialog(
    current: Long,
    mine: Long,
    usersDefault: Long,
    isSelf: Boolean,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets =
        listOf(PowerLevels.ADMIN, PowerLevels.MODERATOR, usersDefault).distinct().filter { it <= mine }
    var custom by remember { mutableStateOf(current !in presets) }
    val number = rememberTextFieldState(current.toString())
    val customLevel =
        number.text
            .toString()
            .toLongOrNull()
            ?.takeIf { it <= mine }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_role)) },
        text = {
            Column {
                presets.forEach { level ->
                    RadioRow(roleName(level, usersDefault), !custom && level == current) {
                        if (level != current) onChoose(level)
                        onDismiss()
                    }
                }
                RadioRow(stringResource(R.string.member_level), custom) { custom = true }
                if (custom) {
                    OutlinedTextField(
                        state = number,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isSelf) {
                    Text(
                        stringResource(R.string.member_demote_self),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (custom) {
                TextButton(enabled = customLevel != null, onClick = {
                    customLevel?.let(onChoose)
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What a level is called: creator, admin, moderator, member, or its number. */
@Composable
internal fun roleName(
    level: Long,
    usersDefault: Long,
): String =
    when {
        level == PowerLevels.CREATOR -> stringResource(R.string.role_creator)
        level >= PowerLevels.ADMIN -> stringResource(R.string.role_admin)
        level >= PowerLevels.MODERATOR -> stringResource(R.string.role_moderator)
        level == usersDefault -> stringResource(R.string.role_user)
        else -> stringResource(R.string.role_custom, level)
    }
