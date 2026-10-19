package pt.aguiarvieira.xmuks.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.account.DEFAULT_GATEWAY

/** Where gomuks sends this device's pushes: its default gateway, or [gateway] (a host). */
@Composable
internal fun PushGatewayRow(
    gateway: String?,
    onSet: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    ListItem(
        supportingContent = { Text(stringResource(R.string.push_gateway_desc)) },
        trailingContent = {
            Text(
                gateway ?: stringResource(R.string.push_gateway_default),
                style = MaterialTheme.typography.labelLarge,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.tappable { editing = true },
    ) { Text(stringResource(R.string.push_gateway)) }
    if (editing) PushGatewayDialog(gateway, onSet) { editing = false }
}

@Composable
private fun PushGatewayDialog(
    current: String?,
    onSet: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = rememberTextFieldState(current.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_gateway)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.push_gateway_help))
                OutlinedTextField(
                    state = text,
                    placeholder = { Text(DEFAULT_GATEWAY) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSet(text.text.toString())
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onSet(null)
                onDismiss()
            }) { Text(stringResource(R.string.push_gateway_use_default)) }
        },
    )
}
