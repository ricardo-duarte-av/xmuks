package pt.aguiarvieira.xmuks.core.call

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.call.media.KeySink
import pt.aguiarvieira.xmuks.core.call.media.MediaKeyManager
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMembership
import pt.aguiarvieira.xmuks.core.protocol.rtc.MediaKeys
import pt.aguiarvieira.xmuks.core.protocol.rtc.MembershipFormat
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class MediaKeyManagerTest {
    private fun member(
        user: String,
        device: String,
        created: Long = 0,
    ) = CallMembership(
        format = MembershipFormat.Legacy,
        roomId = "!r",
        eventId = "\$e",
        key = "_${user}_${device}_m.call",
        userId = user,
        deviceId = device,
        memberId = "$user:$device",
        slotId = "m.call#ROOM",
        application = "m.call",
        intent = "audio",
        transports = emptyList(),
        createdTs = created,
        expiresAt = Long.MAX_VALUE,
        rtcIdentity = "$user:$device",
    )

    private class Recorder : KeySink {
        val local = mutableListOf<Int>()
        val remote = mutableListOf<Pair<String, Int>>()

        override fun setLocalKey(
            index: Int,
            key: ByteArray,
        ) {
            local += index
        }

        override fun setRemoteKey(
            rtcIdentity: String,
            index: Int,
            key: ByteArray,
        ) {
            remote += rtcIdentity to index
        }
    }

    private val me = member("@me:hs", "ME")
    private val bob = member("@bob:hs", "B")
    private val carol = member("@carol:hs", "C")

    @Test
    fun `first key is used at once and sent to everyone`() =
        runTest {
            val sent = mutableListOf<Pair<List<String>, Int>>()
            val sink = Recorder()
            val keys = manager(sent, sink, clock = { currentTime })
            keys.onMembers(listOf(me, bob))
            assertEquals(listOf(0), sink.local)
            assertEquals(listOf(listOf("@bob:hs") to 0), sent)
        }

    @Test
    fun `a joiner within the grace period gets the current key, later ones force a rotation`() =
        runTest {
            val sent = mutableListOf<Pair<List<String>, Int>>()
            val sink = Recorder()
            val keys = manager(sent, sink, clock = { currentTime })
            keys.onMembers(listOf(me, bob))
            advanceTimeBy(5_000)
            keys.onMembers(listOf(me, bob, carol))
            assertEquals(listOf("@carol:hs") to 0, sent.last())

            advanceTimeBy(20_000)
            val dave = member("@dave:hs", "D")
            keys.onMembers(listOf(me, bob, carol, dave))
            assertEquals(listOf("@bob:hs", "@carol:hs", "@dave:hs") to 1, sent.last())
            // Our own frames switch to the new key a moment later, once it has had time to arrive.
            assertEquals(listOf(0), sink.local)
            advanceTimeBy(MediaKeyManager.USE_KEY_DELAY_MS + 1)
            runCurrent()
            assertEquals(listOf(0, 1), sink.local)
        }

    @Test
    fun `someone leaving rotates the key`() =
        runTest {
            val sent = mutableListOf<Pair<List<String>, Int>>()
            val keys = manager(sent, Recorder(), clock = { currentTime })
            keys.onMembers(listOf(me, bob, carol))
            keys.onMembers(listOf(me, bob))
            assertEquals(listOf("@bob:hs") to 1, sent.last())
        }

    @Test
    fun `keys from members not yet known are held until they show up`() =
        runTest {
            val sink = Recorder()
            val keys = manager(mutableListOf(), sink, clock = { currentTime })
            keys.onMembers(listOf(me))
            val content = MediaKeys.content("!r", "B", "@bob:hs:B", 4, Base64.getEncoder().encodeToString(ByteArray(16)), 1)
            keys.onKeyEvent("@bob:hs", content)
            assertTrue(sink.remote.isEmpty())
            keys.onMembers(listOf(me, bob))
            assertEquals(listOf("@bob:hs:B" to 4), sink.remote)
        }

    @Test
    fun `keys for another room are ignored`() =
        runTest {
            val sink = Recorder()
            val keys = manager(mutableListOf(), sink, clock = { currentTime })
            keys.onMembers(listOf(me, bob))
            keys.onKeyEvent("@bob:hs", MediaKeys.content("!other", "B", "x", 0, "AAAA", 1))
            assertTrue(sink.remote.isEmpty())
        }

    private fun TestScope.manager(
        sent: MutableList<Pair<List<String>, Int>>,
        sink: KeySink,
        clock: () -> Long,
    ) = MediaKeyManager(
        roomId = "!r",
        ownUserId = "@me:hs",
        ownDeviceId = "ME",
        ownMemberId = "@me:hs:ME",
        sender = { targets, content: JsonObject ->
            val index = MediaKeys.parse("@me:hs", content)!!.index
            sent += targets.map { it.userId } to index
            Result.success(Unit)
        },
        sink = sink,
        scope = this,
        clock = clock,
    )
}
