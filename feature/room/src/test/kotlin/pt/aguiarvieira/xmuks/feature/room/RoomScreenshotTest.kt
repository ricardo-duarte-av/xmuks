package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
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
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.Poll
import pt.aguiarvieira.xmuks.core.data.timeline.PollAnswer
import pt.aguiarvieira.xmuks.core.data.timeline.PollTally
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.Reader
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyPreview
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderLabel
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RoomScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val t0 =
        LocalDateTime
            .of(2026, 9, 26, 21, 40)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    private var n = 0

    private fun msg(
        content: MessageContent,
        sender: String = "@ann:x",
        name: String = "Ann",
        me: Boolean = false,
        first: Boolean = true,
        last: Boolean = true,
        reply: ReplyPreview? = null,
        reactions: List<Reaction> = emptyList(),
        edited: Boolean = false,
        profileId: String? = null,
        profileName: String? = null,
        readBy: List<Reader> = emptyList(),
        sendState: SendState = SendState.Sent,
    ) = TimelineItem.Message(
        key = "m${n++}",
        eventId = "\$e$n",
        sender = sender,
        label = SenderLabel(sender, name, profileId, profileName),
        senderAvatarMxc = null,
        fromMe = me,
        timestamp = t0 + n * 60_000L,
        content = content,
        reply = reply,
        reactions = reactions,
        edited = edited,
        firstInGroup = first,
        lastInGroup = last,
        readBy = readBy,
        sendState = sendState,
        localId = if (sendState == SendState.Sent) null else "local$n",
        sendError = null,
    )

    private fun state(
        key: String,
        actor: String,
        name: String,
        change: Change,
    ) = TimelineItem.StateChange(key, "\$$key", actor, name, null, change, t0 + n++ * 60_000L)

    private fun text(
        body: String,
        html: String? = null,
        kind: TextKind = TextKind.Text,
        big: Boolean = false,
    ) = MessageContent.Text(body, html, kind, big)

    private val photo =
        Media("mxc://x/p", false, "image/jpeg", 800, 600, 120_000, "LEHV6nWB2yk8pyo0adR*.7kCMdnj", null, false)

    // Oldest first here; the screen takes newest first.
    private val timeline =
        listOf(
            TimelineItem.DaySeparator("d", LocalDate.of(2026, 9, 26)),
            state("s1", "@bob:x", "Bob", Change.Joined),
            msg(text("Did the SSE keepalive fix land?"), first = true, last = false),
            msg(text("<b>bold</b> and a https://example.org link", "<p><b>Bold</b>, <code>code</code> and a <a href=\"https://example.org\">link</a></p>"), first = false),
            msg(
                text("It did, pushed this morning."),
                sender = "@me:x",
                name = "Me",
                me = true,
                reply = ReplyPreview("\$e1", SenderLabel("@ann:x", "Ann"), "Did the SSE keepalive fix land?"),
                reactions = listOf(Reaction("🎉", 2, true), Reaction("👍", 1, false)),
                edited = true,
                readBy = listOf("Ann", "Bob", "Cat", "Dan").map { Reader("@${it.lowercase()}:x", it, null) },
            ),
            msg(MessageContent.Image(photo, "the view from here"), sender = "@bob:x", name = "Bob"),
            msg(MessageContent.File(photo.copy(size = 2_400_000), "gomuks-logs.tar.zst"), sender = "@bob:x", name = "Bob"),
            msg(
                text("sends another one", "sends <b>another</b> one 🎉", kind = TextKind.Emote),
                sender = "@bob:x",
                name = "Bob",
            ),
            msg(text("waves", kind = TextKind.Emote), sender = "@me:x", name = "Ricardo", me = true),
            state("s2", "@ann:x", "Annie", Change.ProfileChanged("Ann", "Annie", null, null, nameChanged = true, avatarChanged = false)),
            state("s4", "@cat:x", "Cat", Change.ProfileChanged(null, null, "mxc://x/a", "mxc://x/b", nameChanged = false, avatarChanged = true)),
            state("s3", "@mod:x", "Mod", Change.Kicked("Spammer", "spam")),
            msg(
                text("[xmuks] ricardo-duarte-av pushed 1 commit to main", kind = TextKind.Notice),
                sender = "@github:x",
                name = "GitHub Bot",
                profileId = "185400624",
                profileName = "ricardo-duarte-av",
                readBy = listOf(Reader("@ann:x", "Ann", null), Reader("@cat:x", "Cat", null)),
            ),
            msg(text("🚀", big = true), sender = "@me:x", name = "Me", me = true),
            msg(MessageContent.Redacted),
            msg(text("on its way"), sender = "@me:x", name = "Me", me = true, sendState = SendState.Sending),
            msg(text("did this arrive?"), sender = "@me:x", name = "Me", me = true, sendState = SendState.Unknown),
        )

    private val room =
        RoomSummary("!r", "xmuks test", null, false, true, null, false, Preview.Text(""), t0, Unread())

    private fun shoot(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { XmuksTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Screen(
        items: List<TimelineItem>?,
        typing: List<String> = emptyList(),
    ) = RoomScreen(
        roomId = "!r",
        sharedScope = "chats",
        room = room,
        timeline = TimelineState(items),
        context = null,
        typing = typing,
        resolver = MediaResolver({ null }, { _, _ -> null }),
        onBack = {},
        onLoadOlder = {},
        onOpenMedia = {},
        onOpenUser = {},
        onShowContext = {},
        onLeaveContext = {},
        composer = ComposerActions(TextFieldState(), {}, {}, {}),
    )

    @Test
    fun timeline() = shoot("room_timeline") { Screen(timeline.asReversed(), typing = listOf("Bob")) }

    @Test
    fun timelineDark() = shoot("room_timeline_dark", dark = true) { Screen(timeline.asReversed()) }

    @Test
    fun loading() = shoot("room_loading") { Screen(null) }

    @Test
    fun polls() =
        shoot("room_polls") {
            val answers = listOf(PollAnswer("a", "Pizza"), PollAnswer("b", "Sushi"), PollAnswer("c", "Soup"))
            Screen(
                listOf(
                    msg(
                        MessageContent.Poll(Poll("Lunch?", answers, 1, true), PollTally(mapOf("a" to 1, "b" to 3), setOf("b"), 4)),
                        sender = "@bob:x",
                        name = "Bob",
                    ),
                    msg(
                        MessageContent.Poll(Poll("Toppings?", answers, 3, false), PollTally(mine = setOf("a", "c"), voters = 2)),
                        me = true,
                    ),
                    msg(
                        MessageContent.Poll(Poll("Where?", answers.take(2), 1, true), PollTally(mapOf("a" to 2), voters = 2, ended = true)),
                        sender = "@bob:x",
                        name = "Bob",
                    ),
                ).asReversed(),
            )
        }
}
