package pt.aguiarvieira.xmuks.core.call.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.CallPhase
import pt.aguiarvieira.xmuks.core.call.CallSession

/**
 * Registers our calls with the system through Jetpack Telecom (self-managed): that's what makes a
 * call real to everything that isn't our UI — Bluetooth headsets and car kits, Wear, the system's
 * audio routing, and a cellular call arriving mid-call. Telecom owns the audio (mode and route)
 * while it has the call.
 *
 * Best-effort throughout: a device or permission without Telecom must never stop a call; it then
 * just runs without it (and LiveKit routes audio itself).
 */
class TelecomCall(
    private val context: Context,
    private val audio: CallAudio,
) {
    private val manager by lazy { CallsManager(context) }
    private var registered = false

    /** Whether calls can go through Telecom here (checked before media starts, to pick who routes audio). */
    fun available(): Boolean {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.MANAGE_OWN_CALLS)
        if (permission != PackageManager.PERMISSION_GRANTED) return false
        if (!registered) {
            registered =
                runCatching {
                    manager.registerAppWithTelecom(
                        CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING,
                    )
                }.onFailure { Log.w(TAG, "Telecom registration failed", it) }.isSuccess
        }
        return registered
    }

    /**
     * Holds a Telecom call for [session] until it ends; suspends that long. [onRemoteHangUp] is a
     * hang-up from outside the app (headset, car, the system making room for a phone call).
     */
    suspend fun run(
        session: CallSession,
        incoming: Boolean,
        video: Boolean,
        onRemoteHangUp: () -> Unit,
    ) {
        val room = session.room
        val attributes =
            CallAttributesCompat(
                displayName = room.name,
                // Matrix has no dialable address: a DM is a call to a person, a group call to a room.
                address =
                    room.dmUserId?.let { Uri.fromParts("matrix", "u/${it.removePrefix("@")}", null) }
                        ?: Uri.fromParts("matrix", "roomid/${room.roomId.removePrefix("!")}", null),
                direction =
                    if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
                callType = callType(video),
            )
        runCatching {
            manager.addCall(
                attributes,
                onAnswer = { },
                onDisconnect = { cause ->
                    Log.i(TAG, "Telecom ended the call: $cause")
                    onRemoteHangUp()
                },
                onSetActive = { },
                onSetInactive = { },
            ) {
                launch {
                    setActive()
                    follow(this@addCall, session)
                }
                launch {
                    session.phase.collect { phase ->
                        if (phase is CallPhase.Ended) disconnect(DisconnectCause(DisconnectCause.LOCAL))
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Telecom call failed; carrying on without it", it) }
        audio.detach()
    }

    private fun CoroutineScope.follow(
        scope: CallControlScope,
        session: CallSession,
    ) {
        audio.attach { route ->
            launch {
                val target = lastEndpoints.firstOrNull { it.identifier.toString() == route.id } ?: return@launch
                scope.requestEndpointChange(target)
            }
        }
        launch {
            scope.availableEndpoints.collect { list ->
                lastEndpoints = list
                audio.update(routes = list.map(::route))
            }
        }
        launch { scope.currentCallEndpoint.collect { audio.update(current = route(it)) } }
        // Muted from outside (a headset button, the car): mute us too. Our own mute stays ours.
        launch {
            scope.isMuted
                .distinctUntilChanged()
                .drop(1)
                .collect { muted -> session.setMicrophone(!muted) }
        }
        // Camera on and off: tell the system it's become a video call (or an audio one), which also
        // moves audio to the speaker the way the platform does for video calls.
        launch {
            session.cameraOn.drop(1).collect { on ->
                scope.requestCallType(callType(on))
            }
        }
    }

    private fun callType(video: Boolean) =
        if (video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    @Volatile private var lastEndpoints: List<CallEndpointCompat> = emptyList()

    private fun route(e: CallEndpointCompat) =
        AudioRoute(
            id = e.identifier.toString(),
            name = e.name.toString(),
            kind =
                when (e.type) {
                    CallEndpointCompat.TYPE_EARPIECE -> RouteKind.Earpiece
                    CallEndpointCompat.TYPE_SPEAKER -> RouteKind.Speaker
                    CallEndpointCompat.TYPE_BLUETOOTH -> RouteKind.Bluetooth
                    CallEndpointCompat.TYPE_WIRED_HEADSET -> RouteKind.WiredHeadset
                    else -> RouteKind.Other
                },
        )

    private companion object {
        const val TAG = "TelecomCall"
    }
}
