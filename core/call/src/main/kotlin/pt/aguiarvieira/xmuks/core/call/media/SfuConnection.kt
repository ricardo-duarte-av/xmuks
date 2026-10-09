package pt.aguiarvieira.xmuks.core.call.media

import android.content.Context
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioHandler
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.VideoPreset169

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

    suspend fun connect() {
        room.connect(access.url, access.jwt, ConnectOptions(autoSubscribe = true))
    }

    fun close() {
        room.disconnect()
        room.release()
    }
}
