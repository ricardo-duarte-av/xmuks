package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.call.CallParticipant
import pt.aguiarvieira.xmuks.core.call.CallPhase
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.call.system.RouteKind
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CallScreenshotTest {
    private companion object {
        const val NOW = 1_800_000_000_000L
    }

    @get:Rule val compose = createComposeRule()

    private fun tile(
        user: String,
        name: String,
        local: Boolean = false,
        speaking: Boolean = false,
        mic: Boolean = true,
        hand: Long? = null,
    ) = CallTile(
        CallParticipant(
            userId = user,
            deviceId = "D",
            isLocal = local,
            intent = "audio",
            connected = true,
            speaking = speaking,
            microphoneOn = mic,
            cameraOn = false,
            video = null,
            videoRoom = null,
            joinedAt = 0,
            handRaisedAt = hand,
        ),
        name = name,
        avatarUrl = null,
    )

    private fun shoot(
        ui: CallUi,
        file: String,
    ) {
        compose.setContent {
            XmuksTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalCallClock provides { NOW }) {
                    CallScreen(ui, onMinimise = {}, onMicrophone = {}, onCamera = {}, onFlipCamera = {}, onHangUp = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test
    fun directAudio() =
        shoot(
            CallUi(
                roomName = "Alice",
                isDirect = true,
                phase = CallPhase.Connected,
                tiles = listOf(tile("@me:hs", "Me", local = true), tile("@alice:hs", "Alice", speaking = true)),
                connectedAt = null,
            ),
            "call_direct_audio",
        )

    @Test
    fun groupAudio() =
        shoot(
            CallUi(
                roomName = "Book club",
                phase = CallPhase.Connected,
                tiles =
                    listOf(
                        tile("@me:hs", "Me", local = true),
                        tile("@alice:hs", "Alice", speaking = true),
                        tile("@bob:hs", "Bob", mic = false, hand = NOW - 42_000),
                        tile("@carol:hs", "Carol"),
                    ),
            ),
            "call_group_audio",
        )

    @Test
    fun durations() {
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("12:34", formatDuration((12 * 60 + 34) * 1_000L))
        assertEquals("1:02:03", formatDuration((3_600 + 2 * 60 + 3) * 1_000L))
    }

    private fun shootLobby(
        choice: LobbyChoice,
        file: String,
    ) {
        compose.setContent {
            XmuksTheme(darkTheme = true, dynamicColor = false) {
                CallLobby(
                    roomName = "Weekly sync",
                    roomId = "!room:hs",
                    roomAvatarUrl = null,
                    people = 3,
                    choice = choice,
                    onChoice = {},
                    routes =
                        listOf(
                            AudioRoute("1", "Earpiece", RouteKind.Earpiece),
                            AudioRoute("2", "Speaker", RouteKind.Speaker),
                            AudioRoute("3", "Pixel Buds", RouteKind.Bluetooth),
                        ),
                    onJoin = {},
                    onCancel = {},
                    preview = { Box(it.background(Color(0xFF3A4A5A))) },
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test
    fun lobbyCameraOff() = shootLobby(LobbyChoice(microphoneOn = false), "call_lobby_camera_off")

    @Test
    fun lobbyCameraOn() =
        shootLobby(
            LobbyChoice(cameraOn = true, route = AudioRoute("2", "Speaker", RouteKind.Speaker)),
            "call_lobby_camera_on",
        )
}
