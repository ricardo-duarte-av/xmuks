package pt.aguiarvieira.xmuks.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.RoomPinning
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItemBuilder
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.time.ZoneOffset

class PinnedItemsTest {
    private fun ev(n: Long) =
        Event(
            rowId = n,
            roomId = "!r",
            eventId = "\$e$n",
            sender = "@bob:x",
            type = "m.room.message",
            timestamp = 1_790_000_000_000L + n,
            content = GomuksJson.parseToJsonElement("""{"msgtype":"m.text","body":"hi $n"}""").jsonObject,
        )

    private fun pinning(
        held: Map<String, Event>,
        current: Map<String, Event>,
    ) = RoomPinning(
        roomId = "!r",
        exec = ExecClient(OkHttpClient(), { null }, Dispatchers.IO),
        pins = emptyFlow(),
        cached = { held[it] },
        fetch = { current[it] },
        items = { TimelineItemBuilder(me = "@me:x", zone = ZoneOffset.UTC).build(it, emptyMap()) },
    )

    private fun List<List<TimelineItem>>.ids() = map { it.filterIsInstance<TimelineItem.Message>().map { m -> m.eventId } }

    private val a = ev(1)
    private val b = ev(2)
    private val all = mapOf(a.eventId to a, b.eventId to b)
    private val pins = listOf(a.eventId, b.eventId)

    @Test
    fun `nothing held - shown once fetched, newest pin first`() =
        runBlocking {
            val shown = pinning(emptyMap(), all).pinnedItems(pins).toList()
            assertEquals(listOf(listOf(b.eventId, a.eventId)), shown.ids())
        }

    @Test
    fun `all held and unchanged - shown once`() =
        runBlocking {
            assertEquals(1, pinning(all, all).pinnedItems(pins).toList().size)
        }

    @Test
    fun `all held but one deleted since - shown, then shown again`() =
        runBlocking {
            val deleted = all + (a.eventId to a.copy(redactedBy = "\$x"))
            assertEquals(2, pinning(all, deleted).pinnedItems(pins).toList().size)
        }

    @Test
    fun `some held - nothing until fetched`() =
        runBlocking {
            assertEquals(1, pinning(mapOf(a.eventId to a), all).pinnedItems(pins).toList().size)
        }

    @Test
    fun `fetch fails - what is held stays`() =
        runBlocking {
            val shown = pinning(all, emptyMap()).pinnedItems(pins).toList()
            assertEquals(listOf(listOf(b.eventId, a.eventId)), shown.ids())
        }
}
