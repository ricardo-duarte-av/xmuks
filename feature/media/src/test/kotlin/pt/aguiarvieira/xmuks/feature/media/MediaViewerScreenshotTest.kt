package pt.aguiarvieira.xmuks.feature.media

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MediaViewerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun imageWhileLoading() {
        val media =
            ViewerMedia(
                kind = ViewerMedia.Kind.Image,
                url = "https://example.invalid/original.jpg",
                blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
                width = 800,
                height = 600,
                title = "ricardo-duarte-av via GitHub Bot",
                subtitle = "26 Sept 2026, 21:44",
            )
        compose.setContent {
            XmuksTheme(darkTheme = true, dynamicColor = false) {
                MediaViewer(media = media, onBack = {}, playerFor = { error("no player for images") })
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/viewer_image_loading.png")
    }
}
