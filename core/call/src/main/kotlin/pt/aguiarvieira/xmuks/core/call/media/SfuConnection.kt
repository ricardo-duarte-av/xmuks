package pt.aguiarvieira.xmuks.core.call.media

import android.content.Context
import android.util.Log
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioHandler
import io.livekit.android.e2ee.E2EEManager
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.VideoPreset169
import livekit.org.webrtc.FrameCryptor

/**
 * One LiveKit connection: to our own SFU (where we publish), or to another member's (subscribe only).
 * Multi-SFU calls have one per distinct SFU, all sharing the participant-keyed media keys.
 */
class SfuConnection(
    context: Context,
    val access: SfuAccess,
    val publishing: Boolean,
    encrypted: Boolean,
    audioHandler: AudioHandler?,
) {
    val room: Room =
        LiveKit.create(
            context.applicationContext,
            RoomOptions(
                adaptiveStream = true,
                dynacast = publishing,
                // As Element Call: RED off (it breaks with E2EE), DTX on, VP8 with simulcast.
                audioTrackPublishDefaults = AudioTrackPublishDefaults(red = false, dtx = true),
                videoTrackPublishDefaults =
                    VideoTrackPublishDefaults(
                        simulcast = true,
                        videoCodec = "vp8",
                        videoEncoding = VideoPreset169.H720.encoding,
                    ),
            ),
            LiveKitOverrides(audioOptions = audioHandler?.let { AudioOptions(audioHandler = it) }),
        )

    /**
     * The room's frame keys (encrypted rooms). Made only once the room exists: creating the room is
     * what loads webrtc's native library, and the key provider is native (on a cold start — answering
     * from a push — making it first fails with "No implementation found").
     */
    val keys: MatrixKeyProvider? =
        if (encrypted) MatrixKeyProvider().also { room.e2eeOptions = E2EEOptions(keyProvider = it) } else null

    /**
     * Encrypt what we send with our key [index] from now on. The SDK picks a sender's key index once, when
     * the track is published, and never moves it (livekit-client on the web does), so after a rotation
     * we'd go on sending under the old key that newcomers never get. Its frame cryptors are private:
     * reach them reflectively, keyed by (track sid, participant identity).
     */
    fun setOwnKey(
        identity: String,
        index: Int,
        key: ByteArray,
    ) {
        keys?.setRawKey(identity, index, key) ?: return
        runCatching {
            val manager = room.e2eeManager ?: return
            val own = room.localParticipant.identity

            @Suppress("UNCHECKED_CAST")
            val cryptors = frameCryptors.get(manager) as Map<Pair<String, Any>, FrameCryptor>
            cryptors.entries
                .toList()
                .filter { (k, _) -> k.second == own }
                .forEach { (_, cryptor) -> cryptor.keyIndex = index }
        }.onFailure { Log.w(TAG, "Couldn't move our sent media to key $index", it) }
    }

    suspend fun connect() {
        room.connect(access.url, access.jwt, ConnectOptions(autoSubscribe = true))
    }

    fun close() {
        room.disconnect()
        room.release()
    }

    private companion object {
        const val TAG = "SfuConnection"

        val frameCryptors by lazy {
            E2EEManager::class.java.getDeclaredField("frameCryptors").apply { isAccessible = true }
        }
    }
}
