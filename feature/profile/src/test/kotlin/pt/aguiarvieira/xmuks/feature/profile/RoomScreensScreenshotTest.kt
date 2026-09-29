package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomMember
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomPreview
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RoomScreensScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val media = ProfileMedia({ null }, { null })

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { XmuksTheme(darkTheme = true, dynamicColor = false, content = content) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun member(
        id: String,
        name: String,
        level: Long,
        membership: Membership = Membership.Join,
    ) = RoomMember("@$id:example.org", name, null, membership, level, null)

    private val info =
        RoomInfo(
            roomId = "!room:example.org",
            name = "Matrix Spec",
            topic = "Discuss the specification and its MSCs",
            avatarMxc = null,
            canonicalAlias = "#matrix-spec:example.org",
            altAliases = emptyList(),
            encrypted = false,
            joinRule = "knock",
            historyVisibility = "shared",
            guestAccess = false,
            roomVersion = "12",
            isSpace = false,
            replacementRoom = null,
            powerLevels =
                PowerLevels(
                    emptyMap(),
                    0,
                    emptyMap(),
                    0,
                    50,
                    50,
                    50,
                    0,
                    50,
                    creators = setOf("@alice:example.org"),
                ),
            members =
                listOf(
                    member("alice", "Alice", PowerLevels.CREATOR),
                    member("bob", "Bob", 100),
                    member("carol", "Carol", 50),
                    member("dan", "Dan", 0),
                    member("erin", "Erin", 0),
                    member("frank", "Frank", 0, Membership.Knock),
                    member("grace", "Grace", 0, Membership.Invite),
                    member("mallory", "Mallory", 0, Membership.Ban),
                ),
        )

    @Test
    fun members() =
        capture("room_members") {
            RoomMembersScreen(
                info = info,
                me = "@dan:example.org",
                media = media,
                actions = RoomInfoActions(),
                busy = false,
                error = null,
                onErrorShow = {},
                onBack = {},
            )
        }

    @Test
    fun preview() =
        capture("room_preview") {
            RoomPreviewScreen(
                roomIdOrAlias = "#matrix-spec:example.org",
                state =
                    PreviewState.Loaded(
                        RoomPreview(
                            roomId = "!room:example.org",
                            name = "Matrix Spec",
                            topic = "Discuss the specification and its MSCs",
                            avatarMxc = null,
                            canonicalAlias = "#matrix-spec:example.org",
                            joinedMembers = 1002,
                            joinRule = "knock_restricted",
                            worldReadable = true,
                            isSpace = false,
                            encrypted = false,
                            membership = "leave",
                        ),
                    ),
                join = JoinState.Idle,
                media = media,
                onJoin = {},
                onKnock = {},
                onErrorShow = {},
                onBack = {},
            )
        }
}
