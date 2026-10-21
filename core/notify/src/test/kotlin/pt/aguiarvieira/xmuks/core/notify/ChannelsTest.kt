package pt.aguiarvieira.xmuks.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun parents() = Channels.ensure(context)

    /** A room's channel as xmuks used to make it, under the group channel. */
    private fun legacy(
        roomId: String,
        change: NotificationChannel.() -> Unit = {},
    ) = manager.createNotificationChannel(
        NotificationChannel("room:$roomId", roomId, NotificationManager.IMPORTANCE_HIGH).apply {
            setConversationId(Channels.GROUP, roomId)
            change()
        },
    )

    /** The once-only pruning, run again. */
    private fun prune() {
        context
            .getSharedPreferences("notify_channels", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Channels.ensure(context)
    }

    @Test
    fun roomsPostToTheirParent() {
        assertEquals(Channels.GROUP, Channels.forRoom(context, "!a:x", "A", direct = false))
        assertEquals(Channels.DM, Channels.forRoom(context, "!b:x", "B", direct = true))
        assertNull(manager.getNotificationChannel("conversation:!a:x"))
    }

    @Test
    fun untouchedLegacyChannelsGoAndChangedOnesStay() {
        legacy("!same:x")
        legacy("!buzz:x") { enableVibration(true) }
        prune()
        assertNull(manager.getNotificationChannel("room:!same:x"))
        assertNotNull(manager.getNotificationChannel("room:!buzz:x"))
    }

    @Test
    fun aKeptLegacyChannelFollowsTheRoomsName() {
        legacy("!buzz:x") { enableVibration(true) }
        prune()
        Channels.forRoom(context, "!buzz:x", "Renamed", direct = false)
        assertEquals("Renamed", manager.getNotificationChannel("room:!buzz:x").name)
    }

    @Test
    fun customizingStartsFromTheParentAsItIsNow() {
        val parent = manager.getNotificationChannel(Channels.GROUP)
        parent.enableVibration(true)
        parent.vibrationPattern = longArrayOf(0, 100)
        manager.createNotificationChannel(parent)
        val id = Channels.customize(context, "!c:x", "C", direct = false)
        assertEquals("conversation:!c:x", id)
        val channel = manager.getNotificationChannel(id)
        assertEquals(Channels.GROUP, channel.parentChannelId)
        assertEquals("!c:x", channel.conversationId)
        assertTrue(channel.shouldVibrate())
        assertArrayEquals(longArrayOf(0, 100), channel.vibrationPattern)
        assertEquals(manager.getNotificationChannel(Channels.GROUP).sound, channel.sound)
    }

    @Test
    fun customizingAKeptLegacyRoomOpensItsChannel() {
        legacy("!buzz:x") { enableVibration(true) }
        prune()
        assertEquals("room:!buzz:x", Channels.customize(context, "!buzz:x", "Buzz", direct = false))
    }
}
