package pt.aguiarvieira.xmuks.feature.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import pt.aguiarvieira.xmuks.core.designsystem.R as DesignR

@Composable
fun LoginRoute(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LoginScreen(
        form = viewModel.form,
        status = status,
        onEdit = viewModel::clearError,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    form: LoginForm,
    status: LoginStatus,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEdit by rememberUpdatedState(onEdit)
    LaunchedEffect(form) {
        snapshotFlow {
            Triple(
                form.server.text.toString(),
                form.username.text.toString(),
                form.password.text.toString()
            )
        }.drop(1)
            .collect { currentOnEdit() }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header()
                OutlinedTextField(
                    state = form.server,
                    label = { Text(stringResource(R.string.login_server)) },
                    placeholder = { Text("gomuks.example.org") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    enabled = !status.busy,
                    isError = status.error == LoginError.InvalidUrl || status.error == LoginError.InsecureUrl,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false
                        ),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                OutlinedTextField(
                    state = form.username,
                    label = { Text(stringResource(R.string.login_username)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    enabled = !status.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                )
                PasswordField(form.password, status, onSubmit)
                AnimatedVisibility(visible = status.error != null) {
                    Text(
                        text = status.error?.let { errorText(it) }.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ConnectButton(enabled = !status.busy && form.isComplete, busy = status.busy, onClick = onSubmit)
                Text(
                    stringResource(R.string.login_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "x",
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            stringResource(R.string.login_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectButton(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(
                stringResource(R.string.login_connect),
                style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)
            )
        }
    }
}

@Composable
private fun PasswordField(
    password: TextFieldState,
    status: LoginStatus,
    onSubmit: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = password,
        label = { Text(stringResource(R.string.login_password)) },
        enabled = !status.busy,
        isError = status.error == LoginError.BadCredentials,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        onKeyboardAction = { onSubmit() },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter =
                        painterResource(
                            if (visible) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility
                        ),
                    contentDescription =
                        stringResource(
                            if (visible) R.string.login_hide_password else R.string.login_show_password
                        ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
    )
}

@Composable
private fun errorText(error: LoginError): String =
    when (error) {
        LoginError.InvalidUrl -> stringResource(R.string.login_error_url)
        LoginError.InsecureUrl -> stringResource(R.string.login_error_insecure)
        LoginError.BadCredentials -> stringResource(R.string.login_error_credentials)
        is LoginError.Other -> stringResource(R.string.login_error_other, error.detail)
    }
