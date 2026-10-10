package pt.aguiarvieira.xmuks.feature.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.call.system.RouteKind

/**
 * What holding the phone to an ear changes, and puts back when it comes away: speaker audio goes to
 * the earpiece and our camera goes off. Whatever someone changed in between is left as they set it.
 */
class EarHandover {
    private var fromSpeaker = false
    private var stoppedCamera = false

    /** At the ear: from the speaker to the earpiece, and the camera off. */
    fun near(
        route: AudioRoute?,
        routes: List<AudioRoute>,
        cameraOn: Boolean,
    ): Change {
        val earpiece = routes.firstOrNull { it.kind == RouteKind.Earpiece }
        fromSpeaker = route?.kind == RouteKind.Speaker && earpiece != null
        stoppedCamera = cameraOn
        return Change(route = earpiece.takeIf { fromSpeaker }, camera = if (cameraOn) false else null)
    }

    /** Away from the ear: undo what [near] did, where it still stands. */
    fun far(
        route: AudioRoute?,
        routes: List<AudioRoute>,
        cameraOn: Boolean,
    ): Change {
        val back = fromSpeaker && route?.kind == RouteKind.Earpiece
        val speaker = routes.firstOrNull { it.kind == RouteKind.Speaker }.takeIf { back }
        val camera = true.takeIf { stoppedCamera && !cameraOn }
        fromSpeaker = false
        stoppedCamera = false
        return Change(route = speaker, camera = camera)
    }

    /** A route to select and a camera state to set; null for each is "leave it". */
    data class Change(
        val route: AudioRoute? = null,
        val camera: Boolean? = null,
    )
}

/**
 * While in the call: held to an ear, the screen goes off (the system's proximity wake lock, which
 * also ignores touches), speaker audio moves to the earpiece and our camera stops; taken away, both
 * come back.
 */
@Composable
internal fun EarProximity(
    ui: CallUi,
    onRoute: (AudioRoute) -> Unit,
    onCamera: (Boolean) -> Unit,
) {
    if (!ui.phase.live) return
    val context = LocalContext.current
    val current by rememberUpdatedState(ui)
    val route by rememberUpdatedState(onRoute)
    val camera by rememberUpdatedState(onCamera)
    val handover = remember { EarHandover() }
    DisposableEffect(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val screen =
            power
                .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
                ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "xmuks:call-proximity")
                ?.apply { setReferenceCounted(false) }
        var near: Boolean? = null
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val now = event.values[0] < minOf(event.sensor.maximumRange, NEAR_CM)
                    if (now == near) return
                    near = now
                    val ui = current
                    val change =
                        if (now) {
                            handover.near(ui.route, ui.routes, ui.cameraOn)
                        } else {
                            handover.far(ui.route, ui.routes, ui.cameraOn)
                        }
                    change.route?.let(route)
                    change.camera?.let(camera)
                }

                override fun onAccuracyChanged(
                    sensor: Sensor,
                    accuracy: Int,
                ) = Unit
            }
        if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        screen?.acquire()
        onDispose {
            sensors.unregisterListener(listener)
            // Leaving the call at the ear: the screen comes back once the phone is taken away.
            if (screen?.isHeld == true) screen.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }
}

private const val NEAR_CM = 5f
