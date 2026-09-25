package pt.aguiarvieira.xmuks.feature.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LoginScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(
        name: String,
        dark: Boolean = false,
        form: LoginForm = LoginForm(),
        status: LoginStatus = LoginStatus(),
    ) {
        compose.setContent {
            XmuksTheme(darkTheme = dark, dynamicColor = false) {
                LoginScreen(form, status, onEdit = {}, onSubmit = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun empty() = capture("login_empty")

    @Test
    fun filledDark() = capture("login_filled_dark", dark = true, form = filled("hunter2"))

    @Test
    fun error() =
        capture(
            "login_error",
            form = filled("wrong"),
            status = LoginStatus(error = LoginError.BadCredentials),
        )

    private fun filled(password: String) =
        LoginForm(TextFieldState("gomuks.example.org"), TextFieldState("alice"), TextFieldState(password))
}
