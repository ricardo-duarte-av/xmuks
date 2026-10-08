package pt.aguiarvieira.xmuks.core.call

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.media.SfuTokens
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase

/** The one call we can be in at a time, and the way into it. */
class CallManager(
    private val context: Context,
    private val api: RtcApi,
    private val tokens: SfuTokens,
    private val frames: StreamFrames,
    private val database: XmuksDatabase,
    private val roomCalls: RoomCalls,
    private val scope: CoroutineScope,
) {
    private val mutableActive = MutableStateFlow<CallSession?>(null)

    /** The call we're in (or joining, or just left — until it reports [CallPhase.Ended]). */
    val active: StateFlow<CallSession?> = mutableActive.asStateFlow()

    /** Joins the call in [roomId], starting one if there is none. Hangs up any other call first. */
    fun join(
        roomId: String,
        video: Boolean,
        format: FormatPreference = FormatPreference.Auto,
    ) {
        val current = mutableActive.value
        if (current != null && current.room.roomId == roomId && current.phase.value !is CallPhase.Ended) return
        current?.hangUp()
        scope.launch {
            val meta = database.syncDao().meta()
            val userId = meta?.userId ?: return@launch
            val deviceId = meta.deviceId ?: return@launch
            val entity = database.roomListDao().room(roomId).firstOrNull() ?: return@launch
            val room =
                CallRoom(
                    roomId = roomId,
                    name = entity.name ?: roomId,
                    isDirect = entity.dmUserId != null,
                    encrypted = entity.encrypted,
                    dmUserId = entity.dmUserId,
                )
            val session =
                CallSession(
                    room,
                    CallIdentity(userId, deviceId),
                    api,
                    tokens,
                    frames.frames,
                    context,
                    roomCalls,
                    format
                )
            mutableActive.value = session
            session.start(video)
            // Forget the session once it has ended, unless another call replaced it meanwhile.
            session.phase.first { it is CallPhase.Ended }
            mutableActive.compareAndSet(session, null)
        }
    }

    fun hangUp() {
        mutableActive.value?.hangUp()
    }
}
