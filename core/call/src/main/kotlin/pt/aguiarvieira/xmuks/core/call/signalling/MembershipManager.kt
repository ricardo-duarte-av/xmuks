package pt.aguiarvieira.xmuks.core.call.signalling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.MembershipFormat
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/** How long our leave waits, and how often we push it back, while we're alive. */
data class DelayTimings(
    val delayMs: Long,
    val restartMs: Long,
) {
    companion object {
        /** Element Call's defaults: gone 18 s after we stop answering. */
        val NORMAL = DelayTimings(delayMs = 18_000, restartMs = 4_000)

        /** When the JWT service restarts the leave for us while we're connected to the SFU. */
        val DELEGATED = DelayTimings(delayMs = 3_600_000, restartMs = 300_000)
    }
}

/**
 * Keeps our own call membership in the room for as long as we're in the call, in either format,
 * the way matrix-js-sdk's MembershipManager does:
 *
 * 1. schedule our leave as a delayed event (MSC4140), so a crash or lost network still leaves;
 * 2. send the membership;
 * 3. keep restarting the delayed leave; reschedule it if the server forgot it;
 * 4. re-send the membership before it expires (legacy `expires`, sticky duration);
 * 5. on leave, fire the delayed leave now (or send a leave ourselves if that fails).
 */
class MembershipManager(
    private val api: RtcApi,
    private val roomId: String,
    private val userId: String,
    private val deviceId: String,
    val format: MembershipFormat,
    /** Sticky: a fresh UUID per join. Legacy: `@user:DEVICE`. */
    val memberId: String,
    private val transports: List<RtcTransport>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val roomVersion: String? = null,
) {
    @Volatile var timings: DelayTimings = DelayTimings.NORMAL

    /** The delayed leave currently scheduled, for delegating it to the SFU. */
    @Volatile var delayId: String? = null
        private set

    /** Our current membership event, which ring notifications and reactions reference. */
    @Volatile var membershipEventId: String? = null
        private set

    private val mutex = Mutex()
    private var intent: String = RtcTypes.INTENT_AUDIO
    private var createdTs: Long? = null
    private var legacyExpiryIterations = 1
    private var heartbeat: Job? = null
    private var refresher: Job? = null
    private var joined = false

    private val stateKey = CallMemberships.legacyStateKey(userId, deviceId, roomVersion)

    /** Schedules the delayed leave, then joins. Returns our membership event id. */
    suspend fun join(intent: String): Result<String> =
        mutex.withLock {
            this.intent = intent
            scheduleDelayedLeave().onFailure { return Result.failure(it) }
            val sent = sendMembership().onFailure { return Result.failure(it) }
            joined = true
            createdTs = clock()
            startHeartbeat()
            startRefresher()
            sent
        }

    /** Audio ↔ video: other clients show the intent, and Element Call updates it on every camera toggle. */
    suspend fun updateIntent(intent: String) {
        mutex.withLock {
            if (!joined || intent == this.intent) return
            this.intent = intent
            sendMembership()
        }
    }

    /** Leaves: the delayed leave is sent now, so the server-side copy and ours can't both apply. */
    suspend fun leave() {
        mutex.withLock {
            heartbeat?.cancel()
            refresher?.cancel()
            if (!joined) {
                delayId?.let { api.updateDelayed(it, DelayAction.Cancel) }
                return
            }
            joined = false
            val sentDelayed = delayId?.let { api.updateDelayed(it, DelayAction.Send) is ExecResult.Ok } == true
            if (!sentDelayed) sendLeave()
            delayId = null
        }
    }

    private suspend fun scheduleDelayedLeave(): Result<String> {
        val result =
            when (format) {
                MembershipFormat.Legacy -> {
                    api.setState(
                        roomId,
                        RtcTypes.LEGACY_MEMBER,
                        stateKey,
                        JsonObject(emptyMap()),
                        delayMs = timings.delayMs
                    )
                }

                MembershipFormat.Sticky -> {
                    api.sendSticky(
                        roomId,
                        RtcTypes.STICKY_MEMBER,
                        CallMemberships.stickyLeaveContent(memberId),
                        CallMemberships.STICKY_DURATION_MS,
                        delayMs = timings.delayMs,
                    )
                }
            }
        result.onSuccess { delayId = it }
        return result
    }

    private suspend fun sendMembership(): Result<String> {
        val result =
            when (format) {
                MembershipFormat.Legacy -> {
                    val content =
                        CallMemberships.legacyContent(
                            userId,
                            deviceId,
                            intent,
                            transports,
                            expiresMs = CallMemberships.DEFAULT_LEGACY_EXPIRY_MS * legacyExpiryIterations,
                            createdTs = createdTs,
                        )
                    api.setState(roomId, RtcTypes.LEGACY_MEMBER, stateKey, content)
                }

                MembershipFormat.Sticky -> {
                    api.sendSticky(
                        roomId,
                        RtcTypes.STICKY_MEMBER,
                        CallMemberships.stickyContent(userId, deviceId, memberId, intent, transports),
                        CallMemberships.STICKY_DURATION_MS,
                    )
                }
            }
        result.onSuccess { membershipEventId = it }
        return result
    }

    private suspend fun sendLeave() {
        when (format) {
            MembershipFormat.Legacy -> {
                api.setState(roomId, RtcTypes.LEGACY_MEMBER, stateKey, JsonObject(emptyMap()))
            }

            MembershipFormat.Sticky -> {
                api.sendSticky(
                    roomId,
                    RtcTypes.STICKY_MEMBER,
                    CallMemberships.stickyLeaveContent(memberId),
                    CallMemberships.STICKY_DURATION_MS,
                )
            }
        }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat =
            scope.launch {
                while (isActive) {
                    delay(timings.restartMs)
                    restartOnce()
                }
            }
    }

    /** Pushes the delayed leave back; reschedules it when the server no longer knows it. */
    private suspend fun restartOnce() {
        val id = delayId
        if (id == null) {
            mutex.withLock { if (joined) scheduleDelayedLeave() }
            return
        }
        val result = api.updateDelayed(id, DelayAction.Restart)
        // A network error needs nothing: the next beat tries again, well within the delay.
        val forgotten =
            result is ExecResult.CommandError && (result.errcode == "M_NOT_FOUND" || result.status == HTTP_NOT_FOUND)
        if (forgotten) mutex.withLock { if (joined) scheduleDelayedLeave() }
    }

    /** Re-sends the membership shortly before it would lapse. */
    private fun startRefresher() {
        refresher?.cancel()
        refresher =
            scope.launch {
                while (isActive) {
                    val lifetime =
                        when (format) {
                            MembershipFormat.Legacy -> CallMemberships.DEFAULT_LEGACY_EXPIRY_MS
                            MembershipFormat.Sticky -> CallMemberships.STICKY_DURATION_MS
                        }
                    delay(lifetime - REFRESH_HEADROOM_MS)
                    mutex.withLock {
                        if (!joined) return@withLock
                        if (format == MembershipFormat.Legacy) legacyExpiryIterations++
                        sendMembership()
                    }
                }
            }
    }

    private companion object {
        const val REFRESH_HEADROOM_MS = 5_000L
        const val HTTP_NOT_FOUND = 404
    }
}
