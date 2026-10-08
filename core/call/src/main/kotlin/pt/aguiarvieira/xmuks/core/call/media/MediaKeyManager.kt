package pt.aguiarvieira.xmuks.core.call.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMembership
import pt.aguiarvieira.xmuks.core.protocol.rtc.MediaKey
import pt.aguiarvieira.xmuks.core.protocol.rtc.MediaKeys
import pt.aguiarvieira.xmuks.core.protocol.rtc.MembershipFormat
import java.security.SecureRandom
import java.util.Base64

/** Sends our media key to other members' devices (Olm-encrypted to-device). */
fun interface KeySender {
    suspend fun send(
        targets: List<CallMembership>,
        content: JsonObject,
    ): Result<Unit>
}

/** Where keys end up: the SFU connections' frame cryptors. */
interface KeySink {
    fun setLocalKey(
        index: Int,
        key: ByteArray,
    )

    fun setRemoteKey(
        rtcIdentity: String,
        index: Int,
        key: ByteArray,
    )
}

/**
 * Per-participant media keys, as matrix-js-sdk's RTCEncryptionManager handles them:
 *
 *  - our key is 16 random bytes with an index (mod 256), sent to every other member's device;
 *  - someone joins: if our key is younger than [rotationGraceMs], they get the current key,
 *    otherwise everyone gets a new one (so the joiner can't decrypt what was said before);
 *  - someone leaves: everyone gets a new key (so the leaver can't decrypt what comes next);
 *  - our own frames switch to a new key [useKeyDelayMs] after sending it, to let it arrive;
 *  - keys from members we don't know yet are held until their membership shows up.
 */
class MediaKeyManager(
    private val roomId: String,
    private val ownUserId: String,
    private val ownDeviceId: String,
    private val ownMemberId: String,
    private val sender: KeySender,
    private val sink: KeySink,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val rotationGraceMs: Long = ROTATION_GRACE_MS,
    private val useKeyDelayMs: Long = USE_KEY_DELAY_MS,
) {
    private val mutex = Mutex()
    private var members: Map<Pair<String, String>, CallMembership> = emptyMap()
    private var keyIndex = -1
    private var key: ByteArray? = null
    private var keyCreatedAt = 0L
    private val pending = mutableListOf<MediaKey>()

    /** Latest `sent_ts` per (identity, index): older re-deliveries must not undo a rotation. */
    private val newest = HashMap<Pair<String, Int>, Long>()

    /** The room's members changed (ourselves excluded or not; we filter). */
    suspend fun onMembers(all: List<CallMembership>) {
        mutex.withLock {
            val others =
                all.filterNot { it.userId == ownUserId && it.deviceId == ownDeviceId }.associateBy {
                    it.userId to
                        it.deviceId
                }
            val joined = others.filter { (k, m) -> members[k]?.let(::session) != session(m) }.values.toList()
            val left = members.keys - others.keys
            members = others
            flushPending()
            when {
                key == null -> rotate(others.values.toList())
                left.isNotEmpty() -> rotate(others.values.toList())
                joined.isEmpty() -> Unit
                clock() - keyCreatedAt < rotationGraceMs -> sendCurrent(joined)
                else -> rotate(others.values.toList())
            }
        }
    }

    /**
     * What makes a membership a new session of the same device: a legacy rejoin keeps its member id
     * but gets a new `created_ts`; a sticky one gets a new member id (and refreshes its timestamp hourly).
     */
    private fun session(m: CallMembership): Any =
        when (m.format) {
            MembershipFormat.Legacy -> m.memberId to m.createdTs
            MembershipFormat.Sticky -> m.memberId
        }

    /** An `io.element.call.encryption_keys` to-device event (already decrypted by gomuks). */
    suspend fun onKeyEvent(
        sender: String,
        content: JsonObject,
    ) {
        val parsed = MediaKeys.parse(sender, content)?.takeIf { it.roomId == roomId } ?: return
        mutex.withLock {
            if (!deliver(parsed)) pending += parsed
        }
    }

    private fun flushPending() {
        pending.removeAll { deliver(it) }
    }

    private fun deliver(k: MediaKey): Boolean {
        val member = members[k.sender to k.deviceId] ?: return false
        val stamp = k.sentTs ?: clock()
        val slot = member.rtcIdentity to k.index
        if ((newest[slot] ?: Long.MIN_VALUE) > stamp) return true
        newest[slot] = stamp
        val bytes = runCatching { Base64.getDecoder().decode(k.keyBase64) }.getOrNull() ?: return true
        sink.setRemoteKey(member.rtcIdentity, k.index, bytes)
        return true
    }

    private suspend fun rotate(targets: List<CallMembership>) {
        val first = key == null
        val fresh = ByteArray(MediaKeys.KEY_BYTES).also(random::nextBytes)
        keyIndex = (keyIndex + 1) % MediaKeys.INDEX_COUNT
        key = fresh
        keyCreatedAt = clock()
        val index = keyIndex
        sendCurrent(targets)
        // The very first key is used at once: until we have one, nothing of ours may go out.
        if (first) {
            sink.setLocalKey(index, fresh)
        } else {
            scope.launch {
                delay(useKeyDelayMs)
                mutex.withLock { if (keyIndex == index) sink.setLocalKey(index, fresh) }
            }
        }
    }

    private suspend fun sendCurrent(targets: List<CallMembership>) {
        val current = key ?: return
        if (targets.isEmpty()) return
        val content =
            MediaKeys.content(
                roomId = roomId,
                deviceId = ownDeviceId,
                memberId = ownMemberId,
                index = keyIndex,
                keyBase64 = Base64.getEncoder().encodeToString(current),
                sentTs = clock(),
            )
        sender.send(targets, content)
    }

    companion object {
        const val ROTATION_GRACE_MS = 10_000L
        const val USE_KEY_DELAY_MS = 1_000L
    }
}
