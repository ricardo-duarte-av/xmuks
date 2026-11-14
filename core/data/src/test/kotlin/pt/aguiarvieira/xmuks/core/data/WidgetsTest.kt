package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Widget
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class WidgetsTest {
    private var row = 0L

    private fun state(
        key: String,
        content: String,
        type: String = Widget.TYPE,
    ) = Event(
        rowId = ++row,
        roomId = "!r",
        eventId = "\$w$row",
        sender = "@bot:x",
        type = type,
        stateKey = key,
        content = GomuksJson.parseToJsonElement(content).jsonObject,
    )

    @Test
    fun parsesTheArchivistsWidget() {
        val widget =
            Widget
                .of(
                    listOf(
                        state(
                            "archivist-activity",
                            """{"creatorUserId":"@archivist:x","data":{"title":"What the archivist is doing"},
                        "id":"archivist-activity","name":"Archive activity","type":"m.custom",
                        "url":"https://archivist.example/live/","waitForIframeLoad":true}""",
                        ),
                    ),
                ).single()
        assertEquals("archivist-activity", widget.id)
        assertEquals("Archive activity", widget.name)
        assertEquals("What the archivist is doing", widget.title)
        assertEquals("https://archivist.example/live/", widget.url("!r", "@me:x", "Me", null))
    }

    @Test
    fun dropsRemovedAndNonWebWidgets() {
        val widgets =
            Widget.of(
                listOf(
                    state("gone", "{}"),
                    state("js", """{"url":"javascript:alert(1)","name":"Bad"}"""),
                    state("b", """{"url":"https://b.example","name":"beta"}"""),
                    state("a", """{"url":"http://a.example","name":"Alpha"}"""),
                ),
            )
        assertEquals(listOf("Alpha", "beta"), widgets.map { it.name })
    }

    @Test
    fun specTypeWinsOverTheLegacyOne() {
        val widgets =
            Widget.of(
                listOf(
                    state("w", """{"url":"https://old.example","name":"Old"}"""),
                    state("w", """{"url":"https://new.example","name":"New"}""", type = Widget.SPEC_TYPE),
                ),
            )
        assertEquals(listOf("New"), widgets.map { it.name })
    }

    @Test
    fun fillsInTheVariables() {
        val widget =
            Widget.parse(
                state(
                    "w1",
                    """{"url":"https://w.example/?u=${'$'}matrix_user_id&r=${'$'}matrix_room_id&n=${'$'}matrix_display_name&c=${'$'}conf&t=${'$'}org.matrix.msc2873.client_theme",
                    "data":{"conf":"a b"}}""",
                ),
            )!!
        assertEquals("w.example", widget.name)
        assertEquals(
            "https://w.example/?u=%40me%3Ax&r=%21r&n=Me%20Too&c=a%20b&t=dark",
            widget.url("!r", "@me:x", "Me Too", null, mapOf("org.matrix.msc2873.client_theme" to "dark")),
        )
    }

    @Test
    fun noUrlNoWidget() {
        assertNull(Widget.parse(state("x", """{"name":"Nothing"}""")))
    }
}
