package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.MemberProfile
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItemBuilder
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineSnapshot
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.LocalContent
import java.time.ZoneOffset

class TimelineItemBuilderTest {
    private val builder = TimelineItemBuilder(me = "@me:x", zone = ZoneOffset.UTC)
    private var row = 0L
    private val t0 = 1_790_000_000_000L // 2026-09-21 in UTC

    private fun json(s: String): JsonObject = GomuksJson.parseToJsonElement(s).jsonObject

    private fun ev(
        sender: String = "@bob:x",
        type: String = "m.room.message",
        content: String = """{"msgtype":"m.text","body":"hi"}""",
        ts: Long = t0,
        stateKey: String? = null,
        block: Event.() -> Event = { this },
    ): Event {
        row++
        return Event(
            rowId = row,
            timelineRowId = row,
            roomId = "!r",
            eventId = "\$e$row",
            sender = sender,
            type = type,
            stateKey = stateKey,
            timestamp = ts,
            content = json(content),
        ).block()
    }

    private fun build(
        vararg events: Event,
        extra: List<Event> = emptyList(),
        profiles: Map<String, MemberProfile> = emptyMap(),
    ) = builder.build(
        TimelineSnapshot("!r", events = events.toList(), eventsByRowId = (events.toList() + extra).associateBy { it.rowId }, loaded = true),
        profiles,
    )

    private fun List<TimelineItem>.messages() = filterIsInstance<TimelineItem.Message>()

    @Test
    fun `reactions, edits and redaction events are not shown on their own`() {
        val msg = ev()
        val items =
            build(
                msg,
                ev(type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${msg.eventId}","key":"👍"}}"""),
                ev(content = """{"msgtype":"m.text","body":"* x"}""") { copy(relationType = "m.replace") },
                ev(type = "m.room.redaction", content = "{}"),
            )
        assertEquals(1, items.messages().size)
    }

    @Test
    fun `an edited message shows its latest content and is marked edited`() {
        val edited = """{"msgtype":"m.text","body":"* fixed","m.new_content":{"msgtype":"m.text","body":"fixed"}}"""
        val edit =
            ev(content = edited) {
                copy(relationType = "m.replace", localContent = LocalContent(sanitizedHtml = "<b>fixed</b>"))
            }
        val original = ev(content = """{"msgtype":"m.text","body":"fxied"}""") { copy(lastEditRowId = edit.rowId) }
        val msg = build(original, extra = listOf(edit)).messages().single()
        val text = msg.content as MessageContent.Text
        assertEquals("fixed", text.body)
        assertEquals("<b>fixed</b>", text.html)
        assertTrue(msg.edited)
    }

    @Test
    fun `names come from per-message profile, then per-room member, then localpart`() {
        val profiles = mapOf("@bob:x" to MemberProfile("Bob in this room", "mxc://x/bob"))
        val items =
            build(
                ev(content = """{"msgtype":"m.text","body":"a","com.beeper.per_message_profile":{"id":"cat","displayname":"Cat","avatar_url":"mxc://x/cat"}}"""),
                ev(ts = t0 + 1_000),
                ev(sender = "@stranger:x", ts = t0 + 2_000),
                profiles = profiles,
            ).messages()
        assertEquals(listOf("Cat via Bob in this room", "Bob in this room", "stranger"), items.map { it.senderName })
        assertEquals(listOf("cat", null, null), items.map { it.label.profileId })
        assertEquals(listOf("mxc://x/cat", "mxc://x/bob", null), items.map { it.senderAvatarMxc })
    }

    @Test
    fun `messages group by sender and profile within five minutes, days are separated`() {
        val items =
            build(
                ev(ts = t0),
                ev(ts = t0 + 60_000),
                ev(ts = t0 + 60_000 * 10),
                ev(ts = t0 + 60_000 * 11, content = """{"msgtype":"m.text","body":"b","m.per_message_profile":{"id":"other"}}"""),
                ev(ts = t0 + 86_400_000),
            )
        val msgs = items.messages()
        assertEquals(listOf(true, false, true, true, true), msgs.map { it.firstInGroup })
        assertEquals(listOf(false, true, true, true, true), msgs.map { it.lastInGroup })
        assertEquals(2, items.count { it is TimelineItem.DaySeparator })
    }

    @Test
    fun `replies resolve the original, or say it isn't loaded`() {
        val original = ev(sender = "@ann:x", content = """{"msgtype":"m.text","body":"question?"}""")
        val reply = ev(content = """{"msgtype":"m.text","body":"answer","m.relates_to":{"m.in_reply_to":{"event_id":"${original.eventId}"}}}""")
        val dangling = ev(content = """{"msgtype":"m.text","body":"?","m.relates_to":{"m.in_reply_to":{"event_id":"${'$'}gone"}}}""")
        val msgs = build(reply, dangling, extra = listOf(original)).messages()
        assertEquals("ann", msgs[0].reply!!.senderName)
        assertEquals("question?", msgs[0].reply!!.text)
        assertNull(msgs[1].reply!!.senderName)
    }

    @Test
    fun `reactions are sorted, ours marked, custom emoji recognised`() {
        val msg = ev { copy(reactions = mapOf("👍" to 1, "mxc://x/parrot" to 3)) }
        val mine = ev(sender = "@me:x", type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${msg.eventId}","key":"👍"}}""")
        val reactions = build(msg, mine).messages().single().reactions
        assertEquals(listOf("mxc://x/parrot", "👍"), reactions.map { it.key })
        assertTrue(reactions[0].isImage)
        assertTrue(reactions[1].mine)
        assertFalse(reactions[0].mine)
    }

    @Test
    fun `media, redactions and undecryptable events`() {
        val image =
            ev(
                content =
                    """{"msgtype":"m.image","body":"look at this","filename":"cat.jpg",
                    "file":{"url":"mxc://x/enc"},"info":{"w":800,"h":600,"mimetype":"image/jpeg","xyz.amorgan.blurhash":"LEHV6nWB2y"}}""",
            )
        val redacted = ev { copy(redactedBy = "\$r") }
        val broken = ev(type = "m.room.encrypted", content = """{"algorithm":"m.megolm.v1.aes-sha2"}""") { copy(decryptionError = "no session") }
        val msgs = build(image, redacted, broken).messages()
        val img = msgs[0].content as MessageContent.Image
        assertEquals("mxc://x/enc", img.media.mxc)
        assertTrue(img.media.encrypted)
        assertEquals("look at this", img.caption)
        assertEquals("LEHV6nWB2y", img.media.blurhash)
        assertEquals(MessageContent.Redacted, msgs[1].content)
        assertEquals(MessageContent.Undecryptable("no session"), msgs[2].content)
    }

    @Test
    fun `membership changes`() {
        val items =
            build(
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Ann"}"""),
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Annie"}""") {
                    copy(unsigned = json("""{"prev_content":{"membership":"join","displayname":"Ann"}}"""))
                },
                ev(sender = "@mod:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"leave","reason":"spam"}"""),
            ).filterIsInstance<TimelineItem.StateChange>()
        assertEquals(Change.Joined, items[0].change)
        assertEquals(Change.Renamed("Ann", "Annie"), items[1].change)
        assertEquals(Change.Kicked("Annie", "spam"), items[2].change)
    }

    @Test
    fun `sanitised html wins, then formatted_body, then plain`() {
        val msgs =
            build(
                ev(content = """{"msgtype":"m.text","body":"a.b","format":"org.matrix.custom.html","formatted_body":"<i>raw</i>"}""") {
                    copy(localContent = LocalContent(sanitizedHtml = "<i>clean</i>", wasPlaintext = true))
                },
                ev(content = """{"msgtype":"m.text","body":"x","format":"org.matrix.custom.html","formatted_body":"<i>raw</i>"}"""),
                ev(content = """{"msgtype":"m.text","body":"plain"}"""),
            ).messages()
        assertEquals(listOf("<i>clean</i>", "<i>raw</i>", null), msgs.map { (it.content as MessageContent.Text).html })
    }

    @Test
    fun `gomuks' not-sent placeholder is not an error`() {
        val msgs = build(ev { copy(sendError = "not sent") }, ev { copy(sendError = "M_FORBIDDEN") }).messages()
        assertEquals(listOf(null, "M_FORBIDDEN"), msgs.map { it.sendError })
    }
}
