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
import pt.aguiarvieira.xmuks.core.call.system.CallService
import pt.aguiarvieira.xmuks.core.call.system.TelecomCall
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.data.connection.StreamFrames
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase

/** The one call we can be in at a time, and the way into it. */
class CallManager(
    private val context: Context,
    private val api: RtcApi,
    private val tokens: SfuTokens,
    private val frames: StreamFrames,
    private val database: XmuksDatabase,
    private val roomCalls: RoomCalls,
    private val telecom: TelecomCall,
    private val preferences: PreferenceStore,
    private val scope: CoroutineScope,
) {
    /** The current call's audio goes through Telecom (else LiveKit routes it itself). */
    @Volatile var systemAudio: Boolean = false
        private set

    /** The current call was answered (an incoming ring), rather than started or joined by us. */
    @Volatile var answering: Boolean = false
        private set

    private val mutableActive = MutableStateFlow<CallSession?>(null)

    /** The call we're in (or joining, or just left — until it reports [CallPhase.Ended]). */
    val active: StateFlow<CallSession?> = mutableActive.asStateFlow()

    /** Joins the call in [roomId], starting one if there is none. Hangs up any other call first. */
    fun join(
        roomId: String,
        video: Boolean,
        /** Tell a group room we started a call; null: as the room's preference says. */
        notifyRoom: Boolean? = null,
        answer: Boolean = false,
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
            val format =
                when (preferences.value(Prefs.callFormat).first()) {
                    "legacy" -> FormatPreference.Legacy
                    "sticky" -> FormatPreference.Sticky
                    else -> FormatPreference.Auto
                }
            val notify = notifyRoom ?: preferences.value(Prefs.callNotifyRoom, roomId).first()
            systemAudio = telecom.available()
            answering = answer
            val session =
                CallSession(
                    room,
                    CallIdentity(userId, deviceId),
                    api,
                    tokens,
                    frames.frames,
                    context,
                    roomCalls,
                    format,
                    notifyRoom = notify,
                    systemAudio = systemAudio,
                )
            mutableActive.value = session
            roomCalls.setInCall(roomId)
            session.start(video)
            CallService.start(context)
            // Forget the session once it has ended, unless another call replaced it meanwhile.
            session.phase.first { it is CallPhase.Ended }
            if (mutableActive.compareAndSet(session, null)) roomCalls.setInCall(null)
        }
    }

    fun hangUp() {
        mutableActive.value?.hangUp()
    }
}
