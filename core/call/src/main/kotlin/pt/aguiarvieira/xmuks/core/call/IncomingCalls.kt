package pt.aguiarvieira.xmuks.core.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.call.system.Ringer
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcNotification
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcSignals
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/** A call ringing for us. */
data class IncomingCall(
    val roomId: String,
    /** The ring notification's event id: what a decline refers to. */
    val eventId: String,
    val callerId: String,
    val callerName: String,
    val roomName: String,
    val isDirect: Boolean,
    val video: Boolean,
    val expiresAt: Long,
    /** Ready-to-load avatar (the caller's in a DM, the room's otherwise), if known. */
    val avatarUrl: String? = null,
)

/**
 * Rings for calls: from a push (the app may not be running) or from the stream (it is), whichever
 * comes first — the same ring never twice. It stops when its lifetime ends, when the caller gives
 * up, when another of our devices joins the call, or when we answer or decline.
 */
class IncomingCalls(
    private val api: RtcApi,
    private val roomCalls: RoomCalls,
    private val manager: CallManager,
    private val database: XmuksDatabase,
    private val ringer: Ringer,
    frames: StreamFrames,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableRinging = MutableStateFlow<IncomingCall?>(null)
    val ringing: StateFlow<IncomingCall?> = mutableRinging.asStateFlow()

    /** Rings already handled (rung, answered, declined), so a push and a sync can't ring twice. */
    private val seen = LinkedHashSet<String>()
    private var watcher: Job? = null

    init {
        scope.launch { frames.frames.collect(::onFrame) }
    }

    /** Rings for [call], unless it's stale, already handled, or the call we're in. */
    fun ring(call: IncomingCall) {
        synchronized(seen) {
            if (!seen.add(call.eventId)) return
            while (seen.size > SEEN_LIMIT) seen.remove(seen.first())
        }
        if (call.expiresAt <= clock()) return
        if (manager.active.value
                ?.room
                ?.roomId == call.roomId
        ) {
            return
        }
        mutableRinging.value = call
        ringer.show(call)
        watcher?.cancel()
        watcher = scope.launch { watch(call) }
    }

    /** We're answering: stop ringing and hand back what was ringing. */
    fun answer(roomId: String): IncomingCall? {
        val call = mutableRinging.value?.takeIf { it.roomId == roomId } ?: return null
        stop(call.eventId)
        return call
    }

    /** We're declining: the caller's phone stops too (they see the decline). */
    fun decline(eventId: String? = mutableRinging.value?.eventId) {
        val call = mutableRinging.value?.takeIf { it.eventId == eventId } ?: return
        stop(call.eventId)
        scope.launch {
            val content = RtcSignals.declineContent(call.eventId)
            api
                .sendSticky(call.roomId, RtcTypes.DECLINE, content, RtcSignals.DECLINE_STICKY_MS)
                .onFailure { api.sendEvent(call.roomId, RtcTypes.DECLINE, content) }
        }
    }

    private fun stop(eventId: String) {
        if (mutableRinging.value?.eventId != eventId) return
        mutableRinging.value = null
        watcher?.cancel()
        ringer.cancel()
    }

    private suspend fun watch(call: IncomingCall) {
        val me = database.syncDao().meta()
        withTimeoutOrNull((call.expiresAt - clock()).coerceIn(0, RtcSignals.MAX_LIFETIME_MS)) {
            var seenCall = false
            roomCalls.of(call.roomId).firstOrNull { live ->
                val answeredElsewhere =
                    live?.members?.any { it.userId == me?.userId && it.deviceId != me.deviceId } == true
                val callerGone = seenCall && live == null
                if (live != null) seenCall = true
                answeredElsewhere || callerGone
            }
        }
        stop(call.eventId)
    }

    private suspend fun onFrame(frame: GomuksFrame) {
        val sync = (frame.event as? GomuksEvent.Sync)?.sync ?: return
        if (sync.rooms.isEmpty()) return
        val me = database.syncDao().meta()?.userId ?: return
        sync.rooms.forEach { (roomId, room) -> room.events.forEach { onEvent(roomId, it, me) } }
    }

    private suspend fun onEvent(
        roomId: String,
        event: Event,
        me: String,
    ) {
        when (event.effectiveType) {
            RtcTypes.NOTIFICATION -> {
                RtcSignals
                    .parseNotification(
                        event
                    )?.takeIf { it.ringsFor(me) }
                    ?.let { ringFrom(roomId, it) }
            }

            // Declined on another of our devices.
            RtcTypes.DECLINE -> {
                if (event.sender == me) RtcSignals.declinedNotification(event)?.let(::stop)
            }
        }
    }

    private fun RtcNotification.ringsFor(me: String) = ring && sender != me && mentions(me) && expiresAt > clock()

    private suspend fun ringFrom(
        roomId: String,
        n: RtcNotification,
    ) {
        val entity = database.roomListDao().room(roomId).firstOrNull()
        val name = entity?.name ?: roomId
        val direct = entity?.dmUserId != null
        ring(
            IncomingCall(
                roomId = roomId,
                eventId = n.eventId,
                callerId = n.sender,
                callerName = if (direct) name else n.sender,
                roomName = name,
                isDirect = direct,
                video = n.isVideo,
                expiresAt = n.expiresAt,
            ),
        )
    }

    private companion object {
        const val SEEN_LIMIT = 64
    }
}
