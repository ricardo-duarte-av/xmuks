package pt.aguiarvieira.xmuks.core.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.designsystem.component.DesignCatalog
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DesignCatalogScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = capture(dark = false, name = "design_catalog_light")

    @Test
    fun dark() = capture(dark = true, name = "design_catalog_dark")

    private fun capture(
        dark: Boolean,
        name: String,
    ) {
        compose.setContent {
            XmuksTheme(darkTheme = dark, dynamicColor = false) { DesignCatalog() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
