package pt.aguiarvieira.xmuks.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PreferencesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun account() {
        // One value set in the account, one on this device: "Set here" and "From: This device".
        val layers =
            PrefLayers(
                mapOf(
                    PrefScope.Account to JsonObject(mapOf("send_typing_notifications" to JsonPrimitive(false))),
                    PrefScope.Device to JsonObject(mapOf("send_read_receipts" to JsonPrimitive(false))),
                ),
            )
        compose.setContent {
            XmuksTheme(darkTheme = true, dynamicColor = false) {
                PreferencesScreen(
                    forRoom = false,
                    layers = layers,
                    edits = PrefEdits(),
                    error = null,
                    onErrorShow = {},
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preferences_account.png")
    }
}
