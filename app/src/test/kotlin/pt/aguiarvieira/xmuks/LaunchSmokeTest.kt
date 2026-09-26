package pt.aguiarvieira.xmuks

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start of the real app — Application (Hilt graph, connection wiring), splash, theme, first
 * screen — on Robolectric. Catches launch crashes without a device.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `cold start shows the login screen`() {
        compose.onNodeWithText("Welcome to xmuks").assertExists()
    }
}
