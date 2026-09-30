package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Poll
import pt.aguiarvieira.xmuks.core.data.timeline.PollAnswer
import pt.aguiarvieira.xmuks.core.data.timeline.PollTypes
import pt.aguiarvieira.xmuks.core.data.timeline.pollOf
import pt.aguiarvieira.xmuks.core.data.timeline.tally
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class PollsTest {
    private fun json(s: String) = GomuksJson.parseToJsonElement(s) as JsonObject

    private val poll =
        Poll("Lunch?", listOf(PollAnswer("a", "Pizza"), PollAnswer("b", "Sushi"), PollAnswer("c", "Soup")), 1, true)

    private var row = 1L

    private fun response(
        sender: String,
        ts: Long,
        vararg picks: String,
    ) = Event(
        rowId = row++,
        roomId = "!r",
        eventId = "\$r$row",
        sender = sender,
        type = PollTypes.RESPONSE,
        timestamp = ts,
        content = json("""{"org.matrix.msc3381.poll.response":{"answers":[${picks.joinToString { "\"$it\"" }}]}}"""),
        relatesTo = "\$poll",
        relationType = "m.reference",
    )

    private fun end(
        sender: String,
        ts: Long,
    ) = Event(rowId = row++, roomId = "!r", eventId = "\$e$row", sender = sender, type = PollTypes.END, timestamp = ts, relatesTo = "\$poll", relationType = "m.reference")

    @Test
    fun parsesUnstableStart() {
        val parsed =
            pollOf(
                json(
                    """{"org.matrix.msc3381.poll.start":{"question":{"org.matrix.msc1767.text":"Lunch?"},
                    "kind":"org.matrix.msc3381.poll.undisclosed","max_selections":5,
                    "answers":[{"id":"a","org.matrix.msc1767.text":"Pizza"},{"id":"b","org.matrix.msc1767.text":"Sushi"}]}}""",
                ),
            )!!
        assertEquals("Lunch?", parsed.question)
        assertEquals(listOf("Pizza", "Sushi"), parsed.answers.map { it.text })
        assertEquals(2, parsed.maxSelections)
        assertFalse(parsed.disclosed)
        assertFalse(parsed.stable)
    }

    @Test
    fun parsesStableStart() {
        val parsed =
            pollOf(
                json(
                    """{"m.poll":{"question":{"m.text":[{"body":"Q"}]},"kind":"m.disclosed",
                    "answers":[{"m.id":"x","m.text":[{"mimetype":"text/html","body":"<b>X</b>"},{"body":"X"}]}]}}""",
                ),
            )!!
        assertEquals("Q", parsed.question)
        assertEquals(PollAnswer("x", "X"), parsed.answers.single())
        assertTrue(parsed.disclosed)
        assertTrue(parsed.stable)
    }

    @Test
    fun latestVoteCountsAndVotesAfterTheEndDoNot() {
        val events =
            listOf(
                response("@me", 10, "a"),
                response("@me", 20, "b"),
                response("@bob", 15, "b", "c"),
                response("@eve", 25, "zzz"),
                end("@mallory", 30),
                end("@host", 40),
                response("@late", 50, "a"),
            )
        val t = tally("\$poll", "@host", poll, events, "@me")
        assertEquals(mapOf("b" to 2), t.votes)
        assertEquals(setOf("b"), t.mine)
        assertEquals(2, t.voters)
        assertTrue(t.ended)
    }
}
