package pt.aguiarvieira.xmuks.feature.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.auth.LoginResult
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import javax.inject.Inject

/** The form's text lives in [LoginForm]; this is everything else the screen shows. */
data class LoginStatus(
    val busy: Boolean = false,
    val error: LoginError? = null,
)

class LoginForm(
    val server: TextFieldState = TextFieldState(),
    val username: TextFieldState = TextFieldState(),
    val password: TextFieldState = TextFieldState(),
) {
    val isComplete: Boolean
        get() = server.text.isNotBlank() && username.text.isNotBlank() && password.text.isNotEmpty()
}

sealed interface LoginError {
    data object InvalidUrl : LoginError

    data object InsecureUrl : LoginError

    data object BadCredentials : LoginError

    data class Other(
        val detail: String,
    ) : LoginError
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : ViewModel() {
        val form = LoginForm()

        private val _status = MutableStateFlow(LoginStatus())
        val status: StateFlow<LoginStatus> = _status.asStateFlow()

        fun clearError() = _status.update { it.copy(error = null) }

        fun submit() {
            if (_status.value.busy || !form.isComplete) return
            _status.value = LoginStatus(busy = true)
            viewModelScope.launch {
                val result =
                    session.login(
                        form.server.text.toString(),
                        form.username.text.toString(),
                        form.password.text.toString()
                    )
                val error =
                    when (result) {
                        LoginResult.Success -> null
                        LoginResult.InvalidUrl -> LoginError.InvalidUrl
                        LoginResult.InsecureUrl -> LoginError.InsecureUrl
                        LoginResult.BadCredentials -> LoginError.BadCredentials
                        is LoginResult.Failed -> LoginError.Other(result.reason)
                    }
                // On success the app switches screens (it observes the login state); keep the spinner.
                _status.value = LoginStatus(busy = error == null, error = error)
            }
        }
    }
