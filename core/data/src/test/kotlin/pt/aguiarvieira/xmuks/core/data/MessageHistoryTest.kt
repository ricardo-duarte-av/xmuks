package pt.aguiarvieira.xmuks.core.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.MemberProfile
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.MessageHistory
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItemBuilder
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineSnapshot
import pt.aguiarvieira.xmuks.core.data.timeline.reactionGroups
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.LocalContent
import java.time.ZoneOffset

class MessageHistoryTest {
    private var row = 0L
    private val t0 = 1_790_000_000_000L

    private fun json(s: String): JsonObject = GomuksJson.parseToJsonElement(s).jsonObject

    private fun ev(
        sender: String = "@bob:x",
        type: String = "m.room.message",
        content: String = """{"msgtype":"m.text","body":"hi"}""",
        ts: Long = t0,
    ): Event {
        row++
        return Event(
            rowId = row,
            roomId = "!r",
            eventId = "\$e$row",
            sender = sender,
            type = type,
            timestamp = ts,
            content = json(content),
        )
    }

    private fun history(
        original: Event?,
        related: List<Event>,
    ) = MessageHistory(
        roomId = "!r",
        getEvent = { _, _ -> original },
        related = { _, _ -> related },
        live = flowOf(TimelineSnapshot("!r", loaded = true)),
        items = { TimelineItemBuilder(me = "@me:x", zone = ZoneOffset.UTC).build(it, emptyMap()) },
    )

    @Test
    fun `each version is a full message, the original first, edits as they read`() =
        runBlocking {
            val original =
                ev(content = """{"msgtype":"m.image","body":"cat.png","url":"mxc://x/cat"}""")
                    .copy(reactions = mapOf("👍" to 2))
            val edit =
                ev(
                    content =
                        """{"msgtype":"m.text","body":"* see **this**","m.new_content":{"msgtype":"m.text","body":"see **this**"}}""",
                    ts = t0 + 1000,
                ).copy(
                    relationType = "m.replace",
                    localContent = LocalContent(sanitizedHtml = "see <strong>this</strong> <img src=\"mxc://x/e\">"),
                )
            val stranger = ev(sender = "@eve:x", content = """{"m.new_content":{"msgtype":"m.text","body":"hijack"}}""")
            val versions = history(original, listOf(edit, stranger)).versions(original.eventId, deleted = false)!!

            assertEquals(listOf(false, true), versions.map { it.edit })
            assertTrue(versions[0].message.content is MessageContent.Image)
            val text = versions[1].message.content as MessageContent.Text
            assertEquals("see <strong>this</strong> <img src=\"mxc://x/e\">", text.html)
            assertEquals(t0 + 1000, versions[1].message.timestamp)
            // Nothing of the live message: no reactions, no "(edited)", each its own group.
            assertTrue(versions.all { it.message.reactions.isEmpty() && !it.message.edited && it.message.firstInGroup })
        }

    @Test
    fun `a deleted message shows what it said, or nothing when the server kept nothing`() =
        runBlocking {
            val unredacted = ev(content = """{"msgtype":"m.text","body":"oops"}""").copy(redactedBy = "\$r")
            val shown = history(unredacted, emptyList()).versions(unredacted.eventId, deleted = true)!!
            assertEquals("oops", (shown.single().message.content as MessageContent.Text).body)

            val gone = ev(content = "{}").copy(redactedBy = "\$r")
            assertTrue(history(gone, emptyList()).versions(gone.eventId, deleted = true)!!.isEmpty())
            assertNull(history(null, emptyList()).versions("\$x", deleted = true))
        }

    @Test
    fun `reactions are grouped by key, most reacted first, each person once`() {
        fun reaction(
            sender: String,
            key: String,
            ts: Long,
            extra: String = "",
        ) = ev(
            sender = sender,
            type = "m.reaction",
            content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${'$'}m","key":"$key"}$extra}""",
            ts = ts,
        )
        val events =
            listOf(
                reaction("@a:x", "👍", t0 + 5),
                reaction("@b:x", "❤️", t0 + 1),
                reaction("@c:x", "❤️", t0 + 2),
                reaction("@b:x", "❤️", t0 + 3),
                reaction("@d:x", "mxc://x/party", t0 + 4, extra = ""","com.beeper.reaction.shortcode":":party:""""),
                reaction("@e:x", "👍", t0).copy(redactedBy = "\$r"),
            )
        val groups = reactionGroups(events, mapOf("@b:x" to MemberProfile("Bea", null)))

        assertEquals(listOf("❤️", "mxc://x/party", "👍"), groups.map { it.key })
        assertEquals(listOf("Bea", "c"), groups[0].reactors.map { it.name })
        assertEquals("party", groups[1].shortcode)
        assertTrue(groups[1].isImage)
        assertFalse(groups[2].isImage)
        assertEquals(listOf("@a:x"), groups[2].reactors.map { it.userId })
    }
}
