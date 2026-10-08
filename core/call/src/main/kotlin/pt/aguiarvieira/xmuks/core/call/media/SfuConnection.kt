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
    val keys: MatrixKeyProvider?,
    audioHandler: AudioHandler?,
) {
    val room: Room =
        LiveKit.create(
            context.applicationContext,
            RoomOptions(
                adaptiveStream = true,
                dynacast = publishing,
                e2eeOptions = keys?.let { E2EEOptions(keyProvider = it) },
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

    suspend fun connect() {
        room.connect(access.url, access.jwt, ConnectOptions(autoSubscribe = true))
    }

    fun close() {
        room.disconnect()
        room.release()
    }
}
