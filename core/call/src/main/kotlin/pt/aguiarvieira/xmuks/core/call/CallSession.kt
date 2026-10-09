package pt.aguiarvieira.xmuks.core.call

import android.content.Context
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.call.media.KeySink
import pt.aguiarvieira.xmuks.core.call.media.MediaKeyManager
import pt.aguiarvieira.xmuks.core.call.media.SfuConnection
import pt.aguiarvieira.xmuks.core.call.media.SfuTokens
import pt.aguiarvieira.xmuks.core.call.media.TokenSubject
import pt.aguiarvieira.xmuks.core.call.signalling.CallMembers
import pt.aguiarvieira.xmuks.core.call.signalling.MembershipManager
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.data.calls.RoomCalls
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMembership
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.MembershipFormat
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcSignals
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes
import java.util.UUID

/** Who we are in a call. */
data class CallIdentity(
    val userId: String,
    val deviceId: String,
)

/** The room a call happens in, as the call needs it. */
data class CallRoom(
    val roomId: String,
    val name: String,
    val isDirect: Boolean,
    val encrypted: Boolean,
    /** The other person of a DM. */
    val dmUserId: String?,
)

/** Which membership encoding to send: follow the call (Auto), or force one. */
enum class FormatPreference { Auto, Legacy, Sticky }

/** Why a call ended, for the screen to say so. */
enum class EndReason { HungUp, Declined, NoAnswer, Failed }

sealed interface CallPhase {
    data object Connecting : CallPhase

    data object Connected : CallPhase

    data object Reconnecting : CallPhase

    data class Ended(
        val error: String? = null,
        val reason: EndReason = if (error == null) EndReason.HungUp else EndReason.Failed,
    ) : CallPhase
}

/** One device in the call, joined up from its Matrix membership and its LiveKit participant. */
data class CallParticipant(
    val userId: String,
    val deviceId: String,
    val isLocal: Boolean,
    val intent: String?,
    /** Media connected (we see it on the SFU); false while it's only a membership. */
    val connected: Boolean,
    val speaking: Boolean,
    val microphoneOn: Boolean,
    val cameraOn: Boolean,
    val video: VideoTrack?,
    /** The LiveKit room whose EGL context renders [video]. */
    val videoRoom: Room?,
    val joinedAt: Long,
) {
    val key: String get() = "$userId:$deviceId"
}

/**
 * One call we're in: our membership in the room, our LiveKit connections (one per SFU), media
 * keys when the room is encrypted, and the merged participant list the UI draws.
 */
@Suppress("TooManyFunctions") // one call's lifecycle; splitting would scatter its shared state
class CallSession internal constructor(
    val room: CallRoom,
    private val me: CallIdentity,
    private val api: RtcApi,
    private val tokens: SfuTokens,
    private val frames: Flow<GomuksFrame>,
    private val context: Context,
    private val roomCalls: RoomCalls,
    private val formatPreference: FormatPreference,
    /** Tell a group room when we start a call in it. */
    private val notifyRoom: Boolean,
    /** Telecom owns the audio (mode and route): LiveKit must leave it alone. */
    private val systemAudio: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val members = CallMembers(room.roomId)

    private val mutablePhase = MutableStateFlow<CallPhase>(CallPhase.Connecting)
    val phase: StateFlow<CallPhase> = mutablePhase.asStateFlow()

    private val mutableParticipants = MutableStateFlow<List<CallParticipant>>(emptyList())
    val participants: StateFlow<List<CallParticipant>> = mutableParticipants.asStateFlow()

    private val mutableMicrophone = MutableStateFlow(true)
    val microphoneOn: StateFlow<Boolean> = mutableMicrophone.asStateFlow()

    private val mutableCamera = MutableStateFlow(false)
    val cameraOn: StateFlow<Boolean> = mutableCamera.asStateFlow()

    /** When media first connected, for the call timer. */
    @Volatile var connectedAt: Long? = null
        private set

    private var membership: MembershipManager? = null
    private var keys: MediaKeyManager? = null
    private var primary: SfuConnection? = null

    /** Subscribe-only connections to other members' SFUs, by transport. */
    private val others = HashMap<String, SfuConnection>()
    private var ownIdentity: String? = null
    private var hadOthers = false
    private var ended = false

    /** We joined meaning a video call (camera on at join). */
    @Volatile var startedWithVideo: Boolean = false
        private set

    /** Joins the room's call (starting it if nobody's there), with camera on for [video]. */
    fun start(video: Boolean) {
        startedWithVideo = video
        scope.launch {
            runCatching { join(video) }.onFailure { e ->
                end(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun join(video: Boolean) {
        scope.launch { frames.collect(::onFrame) }
        val state = api.roomState(room.roomId).getOrThrow()
        val sticky = api.stickyEvents(room.roomId).getOrDefault(emptyList())
        lock.withLock { members.load(state, sticky) }
        roomCalls.refresh(room.roomId, state + sticky)

        val present = lock.withLock { members.active(clock()) }.filterNot { it.isOwn() }
        val format = chooseFormat(present)
        val transport = ownTransport()
        val memberId =
            when (format) {
                MembershipFormat.Legacy -> CallMemberships.legacyIdentity(me.userId, me.deviceId)
                MembershipFormat.Sticky -> UUID.randomUUID().toString()
            }
        ownIdentity =
            when (format) {
                MembershipFormat.Legacy -> CallMemberships.legacyIdentity(me.userId, me.deviceId)
                MembershipFormat.Sticky -> CallMemberships.stickyIdentity(me.userId, me.deviceId, memberId)
            }

        connectMedia(transport, format, memberId, video)

        val manager =
            MembershipManager(
                api = api,
                roomId = room.roomId,
                userId = me.userId,
                deviceId = me.deviceId,
                format = format,
                memberId = memberId,
                transports = listOf(transport),
                scope = scope,
                clock = clock,
            )
        membership = manager
        val membershipEvent = manager.join(if (video) RtcTypes.INTENT_VIDEO else RtcTypes.INTENT_AUDIO).getOrThrow()
        mutablePhase.value = CallPhase.Connected
        // We started it: let the others know — a DM rings, a group is told a call started.
        if (present.isEmpty() && (room.isDirect || notifyRoom)) announce(membershipEvent, video)
        membersChanged()
        scope.launch { tickParticipants() }
    }

    /** The ring (or "call started") we sent, while the other side of a DM hasn't joined yet. */
    @Volatile var ringEventId: String? = null
        private set

    private suspend fun announce(
        membershipEvent: String,
        video: Boolean,
    ) {
        val ring = room.isDirect
        val content =
            RtcSignals.notificationContent(
                ring = ring,
                intent = if (video) RtcTypes.INTENT_VIDEO else RtcTypes.INTENT_AUDIO,
                membershipEventId = membershipEvent,
                senderTs = clock(),
            )
        val sticky = RtcSignals.RING_LIFETIME_MS + RtcSignals.NOTIFICATION_STICKY_EXTRA_MS
        val sent =
            api
                .sendSticky(room.roomId, RtcTypes.NOTIFICATION, content, sticky)
                .recoverCatching { api.sendEvent(room.roomId, RtcTypes.NOTIFICATION, content).getOrThrow() }
                .getOrNull() ?: return
        if (!ring) return
        ringEventId = sent
        // Nobody picked up: like a phone, give up after the ring's lifetime.
        scope.launch {
            delay(RtcSignals.RING_LIFETIME_MS)
            if (ringEventId == sent && !hadOthers) endBecause(EndReason.NoAnswer)
        }
    }

    /** The SFU the homeserver offers us to publish on. */
    private suspend fun ownTransport(): RtcTransport =
        api
            .transports()
            .getOrThrow()
            .firstOrNull { it.type == RtcTypes.TRANSPORT_LIVEKIT || it.type == "m.livekit" }
            ?: error("The homeserver offers no call service")

    /** Connects to our SFU, keys first in an encrypted room, and starts publishing. */
    private suspend fun connectMedia(
        transport: RtcTransport,
        format: MembershipFormat,
        memberId: String,
        video: Boolean,
    ) {
        val access =
            tokens
                .access(
                    transport,
                    room.roomId,
                    TokenSubject(me.userId, me.deviceId, memberId),
                    legacy = format == MembershipFormat.Legacy,
                ).getOrThrow()
        val connection =
            SfuConnection(
                context,
                access,
                publishing = true,
                encrypted = room.encrypted,
                audioHandler = if (systemAudio) NoAudioHandler() else null
            )
        primary = connection
        if (room.encrypted) startKeys(memberId)
        connection.connect()
        connectedAt = clock()
        watchConnection(connection.room)
        connection.room.localParticipant.setMicrophoneEnabled(mutableMicrophone.value)
        if (video) setCamera(true)
    }

    /** Follow the call: whatever its first member uses; a new call uses sticky events if the server can. */
    private suspend fun chooseFormat(present: List<CallMembership>): MembershipFormat =
        when (formatPreference) {
            FormatPreference.Legacy -> {
                MembershipFormat.Legacy
            }

            FormatPreference.Sticky -> {
                MembershipFormat.Sticky
            }

            FormatPreference.Auto -> {
                present.firstOrNull()?.format
                    ?: if (api.unstableFeatures().getOrDefault(emptyMap())[STICKY_FEATURE] == true) {
                        MembershipFormat.Sticky
                    } else {
                        MembershipFormat.Legacy
                    }
            }
        }

    private suspend fun startKeys(memberId: String) {
        api.listenToDevice(true)
        keys =
            MediaKeyManager(
                roomId = room.roomId,
                ownUserId = me.userId,
                ownDeviceId = me.deviceId,
                ownMemberId = memberId,
                sender = { targets, content -> sendKeys(targets, content) },
                sink =
                    object : KeySink {
                        override fun setLocalKey(
                            index: Int,
                            key: ByteArray,
                        ) {
                            val identity = ownIdentity ?: return
                            primary?.keys?.setRawKey(identity, index, key)
                        }

                        override fun setRemoteKey(
                            rtcIdentity: String,
                            index: Int,
                            key: ByteArray,
                        ) {
                            primary?.keys?.setRawKey(rtcIdentity, index, key)
                            others.values.forEach { it.keys?.setRawKey(rtcIdentity, index, key) }
                        }
                    },
                scope = scope,
                clock = clock,
            )
    }

    private suspend fun sendKeys(
        targets: List<CallMembership>,
        content: JsonObject,
    ): Result<Unit> {
        val messages =
            targets
                .groupBy { it.userId }
                .mapValues { (_, devices) -> devices.associate { it.deviceId to content } }
        return api.sendToDevice(RtcTypes.ENCRYPTION_KEYS, messages, encrypted = true)
    }

    private suspend fun onFrame(frame: GomuksFrame) {
        val sync = (frame.event as? GomuksEvent.Sync)?.sync ?: return
        val roomSync = sync.rooms[room.roomId]
        val changed = roomSync != null && lock.withLock { members.apply(roomSync) }
        // The person we're ringing declined.
        val ring = ringEventId
        if (ring != null && !hadOthers && roomSync != null) {
            val declined = roomSync.events.any { it.sender != me.userId && RtcSignals.declinedNotification(it) == ring }
            if (declined) endBecause(EndReason.Declined)
        }
        sync.toDevice
            .filter { it.type == RtcTypes.ENCRYPTION_KEYS && it.encrypted }
            .forEach { keys?.onKeyEvent(it.sender, it.content) }
        if (changed && mutablePhase.value !is CallPhase.Ended) membersChanged()
    }

    private suspend fun membersChanged() {
        val active = lock.withLock { members.active(clock()) }
        keys?.onMembers(active)
        val othersNow = active.filterNot { it.isOwn() }
        if (othersNow.isNotEmpty()) {
            hadOthers = true
            ringEventId = null
        }
        connectOtherSfus(othersNow)
        refreshParticipants()
        // A DM call is over when the other person hangs up.
        if (room.isDirect && hadOthers && othersNow.isEmpty()) hangUp()
    }

    /** Multi-SFU: subscribe on every SFU someone publishes to that isn't ours. */
    private suspend fun connectOtherSfus(active: List<CallMembership>) {
        val wanted = active.mapNotNull { it.transport }.distinctBy(::transportKey)
        wanted.filterNot { others.containsKey(transportKey(it)) }.forEach { connectSfu(it) }
    }

    private suspend fun connectSfu(transport: RtcTransport) {
        val own = primary ?: return
        val subject =
            TokenSubject(
                me.userId,
                me.deviceId,
                membership?.memberId ?: CallMemberships.legacyIdentity(me.userId, me.deviceId),
            )
        val access =
            tokens
                .access(transport, room.roomId, subject, legacy = membership?.format == MembershipFormat.Legacy)
                .getOrNull() ?: return
        // The same SFU as ours under another service URL: our connection already sees them.
        if (access.url == own.access.url) {
            others[transportKey(transport)] = own
            return
        }
        val connection =
            SfuConnection(
                context,
                access,
                publishing = false,
                encrypted = room.encrypted,
                audioHandler = NoAudioHandler(),
            )
        others[transportKey(transport)] = connection
        runCatching { connection.connect() }
    }

    private fun transportKey(t: RtcTransport) = t.livekitServiceUrl ?: t.raw.toString()

    private fun watchConnection(lkRoom: Room) {
        scope.launch {
            while (isActive && !ended) {
                val now = lkRoom.state
                if (mutablePhase.value !is CallPhase.Ended && membership != null) {
                    mutablePhase.value =
                        when (now) {
                            Room.State.RECONNECTING -> CallPhase.Reconnecting
                            Room.State.CONNECTED -> CallPhase.Connected
                            else -> mutablePhase.value
                        }
                }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun tickParticipants() {
        while (scope.isActive && !ended) {
            refreshParticipants()
            delay(POLL_MS)
        }
    }

    private suspend fun refreshParticipants() {
        val active = lock.withLock { members.active(clock()) }
        val connections = listOfNotNull(primary) + others.values
        val list =
            active.map { m ->
                if (m.isOwn()) {
                    local(m)
                } else {
                    val found =
                        connections.firstNotNullOfOrNull { c ->
                            c.room.remoteParticipants[Participant.Identity(m.rtcIdentity)]?.let {
                                c to
                                    it
                            }
                        }
                    val lk = found?.second
                    val camera = lk?.getTrackPublication(Track.Source.CAMERA)
                    CallParticipant(
                        userId = m.userId,
                        deviceId = m.deviceId,
                        isLocal = false,
                        intent = m.intent,
                        connected = lk != null,
                        speaking = lk?.isSpeaking == true,
                        microphoneOn = lk?.isMicrophoneEnabled == true,
                        cameraOn = camera != null && !camera.muted,
                        video = (camera?.track as? VideoTrack)?.takeIf { !camera.muted },
                        videoRoom = found?.first?.room,
                        joinedAt = m.createdTs,
                    )
                }
            }
        val withSelf = if (list.none { it.isLocal }) list + local(null) else list
        mutableParticipants.value = withSelf
    }

    private fun local(m: CallMembership?): CallParticipant {
        val lk = primary?.room?.localParticipant
        val camera = lk?.getTrackPublication(Track.Source.CAMERA)
        return CallParticipant(
            userId = me.userId,
            deviceId = me.deviceId,
            isLocal = true,
            intent = m?.intent,
            connected = primary?.room?.state == Room.State.CONNECTED,
            speaking = lk?.isSpeaking == true,
            microphoneOn = mutableMicrophone.value,
            cameraOn = mutableCamera.value,
            video = (camera?.track as? LocalVideoTrack)?.takeIf { mutableCamera.value },
            videoRoom = primary?.room,
            joinedAt = m?.createdTs ?: connectedAt ?: clock(),
        )
    }

    private fun CallMembership.isOwn() = userId == me.userId && deviceId == me.deviceId

    fun setMicrophone(on: Boolean) {
        mutableMicrophone.value = on
        scope.launch { primary?.room?.localParticipant?.setMicrophoneEnabled(on) }
    }

    fun setCamera(on: Boolean) {
        mutableCamera.value = on
        scope.launch {
            primary?.room?.localParticipant?.setCameraEnabled(on)
            membership?.updateIntent(if (on) RtcTypes.INTENT_VIDEO else RtcTypes.INTENT_AUDIO)
            refreshParticipants()
        }
    }

    /** Front ↔ back camera. */
    fun flipCamera() {
        val track =
            primary
                ?.room
                ?.localParticipant
                ?.getTrackPublication(Track.Source.CAMERA)
                ?.track as? LocalVideoTrack
                ?: return
        val facing = track.options.position
        track.switchCamera(position = if (facing == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT)
    }

    /** Leaves the call (the membership first, so others see us go even if media teardown stalls). */
    fun hangUp() {
        if (ended) return
        ended = true
        scope.launch {
            withContext(NonCancellable) {
                runCatching { membership?.leave() }
                if (keys != null) api.listenToDevice(false)
                (others.values.toSet() - setOfNotNull(primary)).forEach { runCatching { it.close() } }
                primary?.let { runCatching { it.close() } }
                if (mutablePhase.value !is CallPhase.Ended) mutablePhase.value = CallPhase.Ended()
            }
            scope.cancel()
        }
    }

    private fun endBecause(reason: EndReason) {
        if (mutablePhase.value !is CallPhase.Ended) mutablePhase.value = CallPhase.Ended(reason = reason)
        hangUp()
    }

    private suspend fun end(error: String) {
        mutablePhase.value = CallPhase.Ended(error)
        hangUp()
    }

    private companion object {
        const val STICKY_FEATURE = "org.matrix.msc4354"
        const val POLL_MS = 250L
    }
}
