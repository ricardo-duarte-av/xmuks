package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.database.RoomEntity
import pt.aguiarvieira.xmuks.core.database.SpaceEdgeEntity
import pt.aguiarvieira.xmuks.core.protocol.Event

class SpaceEditsTest {
    private fun room(
        id: String,
        name: String? = id.removePrefix("!"),
        space: Boolean = false,
        dm: String? = null,
        replacement: String? = null,
    ) = RoomEntity(
        roomId = id,
        name = name,
        nameQuality = 0,
        avatar = null,
        topic = null,
        canonicalAlias = null,
        dmUserId = dm,
        roomType = if (space) "m.space" else null,
        isSpace = space,
        encrypted = false,
        replacementRoom = replacement,
        heroes = "",
        joinedMembers = null,
        invitedMembers = null,
        previewEventRowId = 0,
        sortingTs = 0,
        unreadHighlights = 0,
        unreadNotifications = 0,
        unreadMessages = 0,
        markedUnread = false,
        generation = 0,
    )

    private fun edge(
        space: String,
        child: String,
    ) = SpaceEdgeEntity(space, child, order = "", suggested = false, canonical = false)

    // !top > !mid > !leaf, and !leaf > !top would close a loop.
    private val edges =
        listOf(
            edge("!top", "!mid"),
            edge("!mid", "!leaf"),
            edge("!mid", "!a"),
            edge("!leaf", "!b"),
            edge("!other", "!b"),
            edge("!mid", "!notjoined"),
        )
    private val rooms =
        listOf(
            room("!top", space = true),
            room("!mid", space = true),
            room("!leaf", space = true),
            room("!other", space = true),
            room("!a"),
            room("!b"),
            room("!dm", dm = "@ana:x"),
            room("!old", replacement = "!new"),
        )

    @Test
    fun ancestorsFollowEveryPathUpAndSurviveLoops() {
        assertEquals(setOf("!top", "!mid"), SpaceEdits.ancestors("!leaf", edges))
        val loop = edges + edge("!leaf", "!top")
        assertEquals(setOf("!top", "!mid"), SpaceEdits.ancestors("!leaf", loop))
    }

    @Test
    fun childrenAreDirectAndJoinedOnly() {
        val data = SpaceEdits.data("!mid", rooms, edges)
        // !b sits in !leaf (deeper) and !notjoined isn't a room we're in: neither is a child here.
        assertEquals(setOf("!leaf", "!a"), data.children)
    }

    @Test
    fun theSpaceAndItsAncestorsAreNeverOffered() {
        val ids = SpaceEdits.data("!mid", rooms, edges).candidates.map { it.roomId }
        assertEquals(listOf("!leaf", "!other", "!a", "!b", "!dm"), ids)
    }

    @Test
    fun upgradedRoomsAreOfferedOnlyWhenAlreadyIn() {
        assertEquals(false, SpaceEdits.data("!top", rooms, edges).candidates.any { it.roomId == "!old" })
        val inside = SpaceEdits.data("!top", rooms, edges + edge("!top", "!old"))
        assertEquals(true, inside.candidates.any { it.roomId == "!old" })
        assertEquals(true, "!old" in inside.children)
    }

    @Test
    fun parentsNameOtherSpacesAndKindsAreKept() {
        val byId = SpaceEdits.data("!mid", rooms, edges).candidates.associateBy { it.roomId }
        assertEquals(listOf("leaf", "other"), byId.getValue("!b").parents)
        // Its parent is the space being edited: no hint.
        assertEquals(emptyList<String>(), byId.getValue("!a").parents)
        assertEquals(true, byId.getValue("!dm").isDirect)
        assertEquals(true, byId.getValue("!leaf").isSpace)
    }

    private fun state(
        type: String,
        key: String,
        content: JsonObject,
        sender: String = "@creator:big.org",
    ) = Event(rowId = 0, roomId = "!r", eventId = "\$e$type$key", sender = sender, type = type, stateKey = key, content = content)

    private fun member(userId: String) = state("m.room.member", userId, buildJsonObject { put("membership", JsonPrimitive("join")) })

    @Test
    fun viaStartsWithOursThenThePowerfulThenTheBusiest() {
        val levels =
            buildJsonObject {
                put("users", buildJsonObject { put("@mod:small.org", JsonPrimitive(50)) })
            }
        val info =
            RoomInfo.parse(
                "!r",
                listOf(
                    state("m.room.create", "", buildJsonObject { put("room_version", JsonPrimitive("10")) }),
                    state("m.room.power_levels", "", levels),
                    member("@me:home.org"),
                    member("@mod:small.org"),
                    member("@a:big.org"),
                    member("@b:big.org"),
                    member("@c:mid.org"),
                ),
            )
        assertEquals(listOf("home.org", "small.org", "big.org"), SpaceEdits.viaServers("@me:home.org", info))
        assertEquals(listOf("home.org"), SpaceEdits.viaServers("@me:home.org", info, max = 1))
    }

    @Test
    fun serverOfTakesEverythingAfterTheFirstColon() {
        assertEquals("example.org:8448", SpaceEdits.serverOf("@me:example.org:8448"))
        assertEquals(null, SpaceEdits.serverOf("nonsense"))
    }
}
