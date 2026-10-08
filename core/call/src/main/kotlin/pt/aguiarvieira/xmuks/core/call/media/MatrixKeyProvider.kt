package pt.aguiarvieira.xmuks.core.call.media

import io.livekit.android.e2ee.KeyProvider
import livekit.org.webrtc.FrameCryptorFactory
import livekit.org.webrtc.FrameCryptorKeyDerivationAlgorithm
import livekit.org.webrtc.FrameCryptorKeyProvider

/**
 * LiveKit frame-cryptor keys exactly as Element Call's MatrixKeyProvider sets them: per participant
 * (no shared key), raw 16-byte key material run through HKDF (salt "LKFrameEncryptionKey", 128 zero
 * bytes of info → AES-GCM-128), ratchet window 10, key ring of 256 (the Matrix key index space).
 *
 * LiveKit's own BaseKeyProvider only takes keys as strings (`String.toByteArray()`), which can't carry
 * random bytes — hence this one, which hands raw bytes straight to the native provider.
 */
class MatrixKeyProvider : KeyProvider {
    override val rtcKeyProvider: FrameCryptorKeyProvider =
        FrameCryptorFactory.createFrameCryptorKeyProvider(
            false,
            RATCHET_SALT.toByteArray(),
            RATCHET_WINDOW,
            MAGIC_BYTES.toByteArray(),
            FAILURE_TOLERANCE,
            KEY_RING_SIZE,
            false,
            FrameCryptorKeyDerivationAlgorithm.HKDF,
        )

    override var enableSharedKey: Boolean = false

    private val latest = HashMap<String, Int>()

    @Synchronized
    fun setRawKey(
        participantIdentity: String,
        index: Int,
        key: ByteArray,
    ) {
        latest[participantIdentity] = index
        rtcKeyProvider.setKey(participantIdentity, index, key)
    }

    override fun setSharedKey(
        key: String,
        keyIndex: Int?,
    ): Boolean = false

    override fun ratchetSharedKey(keyIndex: Int?): ByteArray = ByteArray(0)

    override fun exportSharedKey(keyIndex: Int?): ByteArray = ByteArray(0)

    override fun setKey(
        key: String,
        participantId: String?,
        keyIndex: Int?,
    ) {
        if (participantId != null) setRawKey(participantId, keyIndex ?: 0, key.toByteArray())
    }

    override fun ratchetKey(
        participantId: String,
        keyIndex: Int?,
    ): ByteArray = rtcKeyProvider.ratchetKey(participantId, keyIndex ?: 0)

    override fun exportKey(
        participantId: String,
        keyIndex: Int?,
    ): ByteArray = rtcKeyProvider.exportKey(participantId, keyIndex ?: 0)

    override fun setSifTrailer(trailer: ByteArray) = rtcKeyProvider.setSifTrailer(trailer)

    @Synchronized
    override fun getLatestKeyIndex(participantId: String): Int = latest[participantId] ?: 0

    private companion object {
        const val RATCHET_SALT = "LKFrameEncryptionKey"
        const val MAGIC_BYTES = "LK-ROCKS"
        const val RATCHET_WINDOW = 10
        const val FAILURE_TOLERANCE = 10
        const val KEY_RING_SIZE = 256
    }
}
