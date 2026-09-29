package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class UserProfileTest {
    private fun json(text: String): JsonObject = GomuksJson.parseToJsonElement(text).jsonObject

    @Test
    fun `extended fields from several clients`() {
        val profile =
            UserProfile.parse(
                "@a:example.org",
                json(
                    """
                    {"profile": {
                      "displayname": "Alice", "avatar_url": "mxc://example.org/av",
                      "chat.commet.profile_banner": "mxc://example.org/banner",
                      "io.fsky.nyx.pronouns": [{"language": "en", "summary": "she/her"}, {"summary": ""}],
                      "us.cloke.msc4175.tz": "Europe/Lisbon",
                      "org.matrix.msc4426.status": {"text": "at lunch", "emoji": "🍜"},
                      "gay.fomx.biography": {"m.text": [{"body": "hi"}]}
                    },
                    "bio": {"html": "<p>hi</p>", "edit_source": "hi"}}
                    """,
                ),
            )
        assertEquals("Alice", profile.displayName)
        assertEquals("mxc://example.org/banner", profile.bannerMxc)
        assertEquals(listOf(UserProfile.Pronouns("she/her", "en")), profile.pronouns)
        assertEquals("Europe/Lisbon", profile.timezone)
        assertEquals(UserProfile.Status("at lunch", "🍜"), profile.status)
        assertEquals(UserProfile.Bio("<p>hi</p>", "hi"), profile.bio)
    }

    @Test
    fun `bio falls back to other clients' fields, escaping plain text`() {
        val commet =
            UserProfile.parse("@a:b", json("""{"profile": {"chat.commet.profile_bio": {"format": "x", "body": "a<b\nc"}}}"""))
        assertEquals(UserProfile.Bio("a&lt;b<br>c", null), commet.bio)
        val stable =
            UserProfile.parse(
                "@a:b",
                json("""{"profile": {"m.biography": {"m.text": [{"body": "p", "mimetype": "text/plain"}, {"body": "<b>h</b>", "mimetype": "text/html"}]}}}"""),
            )
        assertEquals("<b>h</b>", stable.bio?.html)
        assertNull(UserProfile.parse("@a:b", json("""{"profile": {}}""")).bio)
    }

    @Test
    fun `stable status wins, and an empty one is none`() {
        val both =
            UserProfile.parse(
                "@a:b",
                json("""{"profile": {"m.status": {"text": "new"}, "org.matrix.msc4426.status": {"text": "old"}}}"""),
            )
        assertEquals("new", both.status?.text)
        assertNull(UserProfile.parse("@a:b", json("""{"profile": {"m.status": {"text": " "}}}""")).status)
    }

    @Test
    fun `per-message profiles round-trip, unknown fields kept`() {
        val content =
            json(
                """
                {"default_profile_id": "cat", "other": 1, "profiles": [
                  {"id": "cat", "displayname": "Cat", "avatar_url": "mxc://a/b", "x": true,
                   "triggers": [{"prefix": "c:", "keep_trigger": true}, {}]},
                  {"displayname": "no id"}
                ]}
                """,
            )
        val parsed = PerMessageProfiles.parse(content)
        assertEquals("cat", parsed.default?.id)
        assertEquals(listOf(PerMessageProfile.Trigger("c:", "", keepTrigger = true)), parsed.profiles.single().triggers)
        val out = parsed.toJson()
        assertEquals("1", out["other"].toString())
        assertEquals("true", out["profiles"].toString().substringAfter("\"x\":").take(4))
    }

    @Test
    fun `removing the default profile clears the default`() {
        val profiles =
            PerMessageProfiles(
                "a",
                listOf(PerMessageProfile("a", "A", null, emptyList()), PerMessageProfile("b", "B", null, emptyList())),
            )
        val removed = profiles.remove("a")
        assertNull(removed.defaultId)
        assertFalse("default_profile_id" in removed.toJson())
        val edited = profiles.upsert(PerMessageProfile("b", "Bee", null, emptyList()))
        assertEquals(listOf("A", "Bee"), edited.profiles.map { it.displayName })
        assertTrue(edited.upsert(PerMessageProfile("c", null, null, emptyList())).profiles.size == 3)
    }

    @Test
    fun `picking follows gomuks - room trigger, global trigger, room default, global default`() {
        fun p(
            id: String,
            prefix: String = "",
        ) = PerMessageProfile(id, id, null, if (prefix.isEmpty()) emptyList() else listOf(PerMessageProfile.Trigger(prefix, "")))
        val global = PerMessageProfiles("g", listOf(p("g"), p("t", prefix = "&t")))
        val room = PerMessageProfiles(null, listOf(p("r", prefix = "r:")))
        assertEquals("r", PerMessageProfiles.pick(global, room, "r: hi")?.id)
        assertEquals("t", PerMessageProfiles.pick(global, room, "&t hi")?.id)
        assertEquals("g", PerMessageProfiles.pick(global, room, "hi")?.id)
        // A room's explicit "none" beats the global default.
        assertNull(PerMessageProfiles.pick(global, room.copy(defaultId = ""), "hi"))
        assertEquals("r", PerMessageProfiles.pick(global, room.copy(defaultId = "r"), "hi")?.id)
    }
}
