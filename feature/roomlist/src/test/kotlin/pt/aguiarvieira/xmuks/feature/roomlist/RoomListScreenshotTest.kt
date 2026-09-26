package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.data.rooms.Preview
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.rooms.SpaceSummary
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RoomListScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now =
        LocalDateTime
            .of(2026, 9, 26, 21, 40)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun room(
        id: String,
        name: String,
        preview: String,
        sender: String? = null,
        me: Boolean = false,
        minutesAgo: Long = 5,
        unread: Unread = Unread(),
        dm: Boolean = false,
    ) = RoomSummary(
        roomId = id,
        name = name,
        avatarUrl = null,
        isDirect = dm,
        encrypted = false,
        previewSender = sender,
        previewFromMe = me,
        preview = Preview.Text(preview),
        timestamp = now - minutesAgo * 60_000,
        unread = unread,
    )

    private val chats =
        listOf(
            room("!gomuks", "Gomuks", "pushed a fix for the SSE keepalive", "tulir", unread = Unread(3, 3, 1)),
            room("!xmuks", "xmuks dev", "room list is up", me = true, minutesAgo = 28),
            room(
                "!spec",
                "Matrix Spec",
                "proposal entered final comment period",
                "Alex",
                minutesAgo = 112,
                unread = Unread(12, 12)
            ),
            room(
                "!hq",
                "Matrix HQ",
                "anyone else seeing federation lag?",
                "Sam",
                minutesAgo = 60L * 26,
                unread = Unread(40)
            ),
            room("!test", "xmuks test", "ping", me = true, minutesAgo = 60L * 24 * 4),
            room(
                "!old",
                "A room with a rather long name that must ellipsize",
                "old news",
                "Kim",
                minutesAgo =
                    60L * 24 * 40
            ),
        )

    private val spaces =
        listOf(
            SpaceSummary("!matrix", "Matrix", null, 12, Unread(5, 3, 1)),
            SpaceSummary("!homelab", "Homelab", null, 4, Unread(1)),
            SpaceSummary("!work", "Work", null, 9, Unread()),
            SpaceSummary("!family", "Family", null, 2, Unread(2, 2)),
            SpaceSummary("!rust", "Rust Portugal", null, 3, Unread()),
            SpaceSummary("!photo", "Photography and friends", null, 6, Unread()),
        )

    private fun home(
        tab: HomeTab,
        connection: ConnectionState = ConnectionState.Live,
    ) = HomeUiState(
        tab,
        chats,
        chats.filter { it.isDirect },
        spaces,
        connection,
        refreshing = false,
        account = "daedric"
    )

    private fun capture(
        name: String,
        dark: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent { XmuksTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun chats() = capture("home_chats") { HomeScreen(home(HomeTab.Chats), {}, {}, {}, {}, {}, now = now) }

    @Test
    fun chatsDarkConnecting() =
        capture("home_chats_dark_connecting", dark = true) {
            HomeScreen(
                home(HomeTab.Chats, ConnectionState.Initializing(120, catchup = true)),
                {},
                {},
                {},
                {},
                {},
                now = now
            )
        }

    @Test
    fun spaces() = capture("home_spaces") { HomeScreen(home(HomeTab.Spaces), {}, {}, {}, {}, {}, now = now) }

    @Test
    fun spaceWithChips() =
        capture("space_matrix") {
            SpaceScreen(
                spaceId = "!matrix",
                space = spaces.first(),
                subspaces =
                    listOf(
                        SpaceSummary("!dev", "Development", null, 0, Unread()),
                        SpaceSummary("!community", "Community", null, 0, Unread())
                    ),
                filter = null,
                rooms = chats.take(4),
                onSelect = {},
                onBack = {},
                onOpenRoom = {},
                now = now,
            )
        }

    @Test
    fun rowStates() =
        capture("room_rows") {
            Column {
                chats.forEach { RoomListItem(it, now, onClick = {}) }
                RoomListItem(
                    room("!dm", "Ana Ribeiro", "see you at 8", "Ana", dm = true, unread = Unread(2, 2)),
                    now,
                    onClick = {}
                )
                RoomListItem(
                    room("!marked", "Marked unread", "nothing new", "Bo", unread = Unread(marked = true)),
                    now,
                    onClick = {}
                )
            }
        }
}
