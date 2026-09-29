package pt.aguiarvieira.xmuks.feature.share

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.media.PickedFile
import pt.aguiarvieira.xmuks.core.data.rooms.Preview
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShareScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun item(
        id: String,
        name: String,
        kind: MediaKind,
    ) = SharedItem(id, PickedFile(Uri.parse("file:///$name"), name, null, 1234, kind), preview = null)

    @Test
    fun captions() {
        val room = RoomSummary("!r:x", "TEST7TESTE", null, false, false, null, false, Preview.None, 0, Unread())
        compose.setContent {
            XmuksTheme(darkTheme = true, dynamicColor = false) {
                CaptionScreen(
                    room = room,
                    items =
                        listOf(
                            item("1", "holiday.jpg", MediaKind.Image),
                            item("2", "report.pdf", MediaKind.File),
                            item("3", "clip.mp4", MediaKind.Video),
                        ),
                    text = "Look at these",
                    unreadable = listOf("broken.heic"),
                    onChangeRoom = {},
                    onRemove = {},
                    onSend = {},
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/share_captions.png")
    }
}
