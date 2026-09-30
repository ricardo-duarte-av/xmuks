package pt.aguiarvieira.xmuks.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The paths worth compiling ahead of time: starting up to the room list, scrolling it, and opening
 * a room and scrolling its timeline. Needs the app logged in on the device, with a room whose name
 * starts with TEST7 (opened by searching, so no other room is touched).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT)
            device.findObject(By.scrollable(true))?.let { list ->
                list.fling(Direction.DOWN)
                list.fling(Direction.UP)
            }
            device.findObject(By.text("Search chats"))?.let { search ->
                search.click()
                device.waitForIdle()
                device.findObject(By.focused(true))?.text = "TEST7"
                device.wait(Until.hasObject(By.textStartsWith("TEST7")), TIMEOUT)
                device.pressBack()
                device.findObjects(By.textStartsWith("TEST7")).lastOrNull()?.click()
                device.wait(Until.hasObject(By.text("Message")), TIMEOUT)
                device.findObject(By.scrollable(true))?.let { timeline ->
                    timeline.fling(Direction.UP)
                    timeline.fling(Direction.DOWN)
                }
                device.pressBack()
            }
        }

    private companion object {
        const val PACKAGE = "pt.aguiarvieira.xmuks"
        const val TIMEOUT = 15_000L
    }
}
