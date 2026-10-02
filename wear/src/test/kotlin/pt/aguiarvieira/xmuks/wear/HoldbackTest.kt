package pt.aguiarvieira.xmuks.wear

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.notify.PushDismiss
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.PushPayload
import pt.aguiarvieira.xmuks.core.notify.PushUser

@OptIn(ExperimentalCoroutinesApi::class)
class HoldbackTest {
    private val scope = TestScope()
    private val shown = mutableListOf<String>()
    private val dismissed = mutableListOf<String>()
    private val holdback =
        Holdback(scope, HOLD_MS, show = { messages, _ -> shown += messages.map { it.eventId } }, dismiss = { dismissed += it })

    private fun message(
        id: String,
        ts: Long,
        room: String = ROOM,
    ) = PushMessage(
        timestamp = ts,
        eventId = id,
        roomId = room,
        roomName = "Room",
        sender = PushUser("@a:x", "A"),
        self = PushUser("@me:x", "Me"),
        text = "hi",
    )

    private fun elapse(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun aMessageShowsOnceItsHoldIsOver() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        elapse(HOLD_MS - 1)
        assertEquals(emptyList<String>(), shown)
        elapse(1)
        assertEquals(listOf("\$1"), shown)
    }

    @Test
    fun readElsewhereDuringTheHoldShowsNothing() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        elapse(1_000)
        holdback.receive(PushPayload(dismiss = listOf(PushDismiss(ROOM, "\$1", ts = 100))))
        elapse(HOLD_MS)
        assertEquals(emptyList<String>(), shown)
        assertEquals(listOf(ROOM), dismissed)
    }

    @Test
    fun aDismissalOnlyCoversWhatWasRead() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100), message("\$2", 200))))
        holdback.receive(PushPayload(dismiss = listOf(PushDismiss(ROOM, "\$1", ts = 100))))
        elapse(HOLD_MS)
        assertEquals(listOf("\$2"), shown)
    }

    @Test
    fun aDismissalThatOvertookItsMessageStillCoversIt() {
        holdback.receive(PushPayload(dismiss = listOf(PushDismiss(ROOM, "\$1", ts = 100))))
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        elapse(HOLD_MS)
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun aDismissalWithoutATimeClearsWhatIsHeldButNotWhatComesNext() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        holdback.receive(PushPayload(dismiss = listOf(PushDismiss(ROOM))))
        holdback.receive(PushPayload(messages = listOf(message("\$2", 200))))
        elapse(HOLD_MS)
        assertEquals(listOf("\$2"), shown)
    }

    @Test
    fun otherRoomsAreUntouched() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100, room = "!other:x"))))
        holdback.receive(PushPayload(dismiss = listOf(PushDismiss(ROOM, ts = 500))))
        elapse(HOLD_MS)
        assertEquals(listOf("\$1"), shown)
    }

    @Test
    fun aRepeatedPushShowsOnce() {
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        holdback.receive(PushPayload(messages = listOf(message("\$1", 100))))
        elapse(HOLD_MS)
        assertEquals(listOf("\$1"), shown)
    }

    private companion object {
        const val ROOM = "!room:x"
        const val HOLD_MS = 4_000L
    }
}
