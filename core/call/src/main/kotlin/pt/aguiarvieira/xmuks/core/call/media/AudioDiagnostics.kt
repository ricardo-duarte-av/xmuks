package pt.aguiarvieira.xmuks.core.call.media

import android.util.Log
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Logs what each connection receives (issue #38: crackling voices from other servers): per incoming
 * audio stream, every [PERIOD_MS], the deltas of packets, losses, concealed audio and jitter-buffer
 * stretching, plus every frame-decryption state change. Read it with `adb logcat -s CallAudioStats`.
 */
internal object AudioDiagnostics {
    private const val TAG = "CallAudioStats"
    private const val PERIOD_MS = 5_000L

    fun watch(
        scope: CoroutineScope,
        room: Room,
        label: String,
    ) {
        scope.launch {
            room.events.collect { event ->
                if (event is RoomEvent.TrackE2EEStateEvent) {
                    Log.i(TAG, "[$label] e2ee ${event.participant.identity?.value} ${event.track.kind}: ${event.state}")
                }
            }
        }
        scope.launch {
            val last = HashMap<String, Map<String, Any>>()
            while (isActive) {
                delay(PERIOD_MS)
                if (room.state != Room.State.CONNECTED) continue
                val report = suspendCancellableCoroutine { cont -> room.getSubscriberRTCStats { cont.resume(it) } }
                val stats = report.statsMap.values
                stats
                    .filter { it.type == "inbound-rtp" && it.members["kind"] == "audio" }
                    .forEach { s ->
                        val m = s.members
                        val before = last.put(s.id, m)
                        val codec = stats.firstOrNull { it.id == m["codecId"] }?.members
                        Log.i(TAG, "[$label] ${line(m, before, codec)}")
                    }
                stats
                    .filter {
                        it.type == "transport" || (it.type == "candidate-pair" && it.members["nominated"] == true)
                    }.forEach { Log.i(TAG, "[$label] ${it.type} rtt=${it.members["currentRoundTripTime"]}") }
            }
        }
    }

    private fun line(
        m: Map<String, Any>,
        before: Map<String, Any>?,
        codec: Map<String, Any>?,
    ): String {
        fun d(key: String): String {
            val now = (m[key] as? Number)?.toDouble() ?: return "-"
            val then = (before?.get(key) as? Number)?.toDouble() ?: 0.0
            return "%.0f".format(now - then)
        }
        val delay = (m["jitterBufferDelay"] as? Number)?.toDouble()
        val emitted = (m["jitterBufferEmittedCount"] as? Number)?.toDouble()
        val bufferMs =
            if (delay != null && emitted != null &&
                emitted > 0
            ) {
                "%.0f".format(delay / emitted * 1000)
            } else {
                "-"
            }
        return "audio ssrc=${m["ssrc"]} mid=${m["mid"]} track=${m["trackIdentifier"]} " +
            "codec=${codec?.get(
                "mimeType"
            )}/${codec?.get("clockRate")}/${codec?.get("channels")} ${codec?.get("sdpFmtpLine")} " +
            "pkts=+${d("packetsReceived")} lost=+${d("packetsLost")} discarded=+${d("packetsDiscarded")} " +
            "jitter=${m["jitter"]} bufferMs=$bufferMs " +
            "samples=+${d("totalSamplesReceived")} concealed=+${d("concealedSamples")} " +
            "silentConcealed=+${d("silentConcealedSamples")} concealEvents=+${d("concealmentEvents")} " +
            "inserted=+${d("insertedSamplesForDeceleration")} removed=+${d("removedSamplesForAcceleration")} " +
            "level=${m["audioLevel"]} decoder=${m["decoderImplementation"]}"
    }
}
