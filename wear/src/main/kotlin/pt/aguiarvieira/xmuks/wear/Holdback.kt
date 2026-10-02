package pt.aguiarvieira.xmuks.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.PushPayload

/**
 * Holds every message back for [holdMs] before it shows. gomuks pushes to the phone and the watch
 * alike; when the room is open on any client, that client marks it read at once and gomuks follows
 * with a dismissal. A dismissal arriving during the hold drops the messages it covers, so a room
 * being read elsewhere never buzzes the watch.
 */
class Holdback(
    private val scope: CoroutineScope,
    private val holdMs: Long,
    /** Messages that outlived their hold. */
    private val show: suspend (messages: List<PushMessage>, imageAuth: String?) -> Unit,
    /** A room read elsewhere: its notification goes. */
    private val dismiss: (roomId: String) -> Unit,
) {
    private val held = mutableMapOf<String, PushMessage>()

    /** Per room, the newest read receipt time we were told of: a message older than that is already read. */
    private val readUpTo = mutableMapOf<String, Long>()

    fun receive(payload: PushPayload) {
        val fresh =
            synchronized(this) {
                payload.dismiss.forEach { d ->
                    if (d.ts > 0) {
                        readUpTo[d.roomId] = maxOf(readUpTo[d.roomId] ?: 0, d.ts)
                        held.values.removeAll { it.roomId == d.roomId && it.timestamp <= d.ts }
                    } else {
                        // No time given: everything held for the room is read (but not what's yet to come).
                        held.values.removeAll { it.roomId == d.roomId }
                    }
                }
                // A dismissal can overtake its message (FCM doesn't promise order).
                payload.messages
                    .filter { it.timestamp > (readUpTo[it.roomId] ?: 0) && it.eventId !in held }
                    .onEach { held[it.eventId] = it }
            }
        payload.dismiss.forEach { dismiss(it.roomId) }
        if (fresh.isEmpty()) return
        scope.launch {
            delay(holdMs)
            val due = synchronized(this@Holdback) { fresh.mapNotNull { held.remove(it.eventId) } }
            if (due.isNotEmpty()) show(due, payload.imageAuth)
        }
    }
}
