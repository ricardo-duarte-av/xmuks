package pt.aguiarvieira.xmuks.core.call.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.CallPhase
import pt.aguiarvieira.xmuks.core.call.CallSession
import pt.aguiarvieira.xmuks.core.call.R
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.notify.Avatars
import pt.aguiarvieira.xmuks.core.notify.PeopleUris
import javax.inject.Inject

/**
 * Keeps a call alive while the app isn't on screen. Android only lets an app keep using the
 * microphone and camera from the background while a foreground service of those types runs; this is
 * it. It also carries the ongoing-call notification (CallStyle: the status-bar chip, hang up, mute),
 * holds the call with Telecom, and darkens the screen at your ear in a voice call.
 *
 * Started from the foreground (joining always is), narrowed to the permissions actually granted (a
 * type whose permission is missing throws), and never the reason a call ends: if promotion fails the
 * call carries on with a plain notification.
 */
@AndroidEntryPoint
class CallService : Service() {
    @Inject lateinit var manager: CallManager

    @Inject lateinit var telecom: TelecomCall

    @Inject lateinit var audio: CallAudio

    @Inject lateinit var rooms: RoomListRepository

    @Inject lateinit var images: ImageLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: CallSession? = null
    private var avatar: Bitmap? = null
    private var proximity: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> manager.hangUp()
            ACTION_MUTE -> session?.let { it.setMicrophone(!it.microphoneOn.value) }
            else -> start()
        }
        // The call lives in this process: if it dies there is nothing to bring back.
        return START_NOT_STICKY
    }

    private fun start() {
        val current = manager.active.value
        if (current == null || current.phase.value is CallPhase.Ended) {
            stop()
            return
        }
        if (current === session) return
        session = current
        promote(notification(current, connected = false, muted = false))
        scope.launch { loadAvatar(current) }
        if (manager.systemAudio) {
            scope.launch {
                telecom.run(
                    current,
                    incoming = manager.answering,
                    video = current.startedWithVideo
                ) { current.hangUp() }
            }
        }
        scope.launch {
            combine(current.phase, current.microphoneOn, current.cameraOn, audio.current) { phase, mic, camera, route ->
                if (phase is CallPhase.Ended) {
                    stop()
                    return@combine
                }
                NotificationManagerCompat.from(this@CallService).notifySafely(
                    notification(
                        current,
                        phase == CallPhase.Connected,
                        !mic
                    )
                )
                // At the ear in a voice call: the screen goes dark, as the dialer's does.
                holdProximity(!camera && (route == null || route.kind == RouteKind.Earpiece))
            }.collect { }
        }
    }

    private suspend fun loadAvatar(session: CallSession) {
        val url = rooms.room(session.room.roomId).first()?.avatarUrl ?: return
        val request =
            ImageRequest
                .Builder(this)
                .data(url)
                .allowHardware(false)
                .build()
        val image = images.execute(request).image ?: return
        avatar = image.asDrawable(resources).toBitmap()
    }

    private fun notification(
        session: CallSession,
        connected: Boolean,
        muted: Boolean,
    ): Notification {
        ensureChannel(this)
        val room = session.room
        val person =
            Person
                .Builder()
                .setName(room.name)
                .apply { room.dmUserId?.let { setKey(it).setUri(PeopleUris.forUser(this@CallService, it)) } }
                .setIcon(Avatars.round(avatar, room.name, room.dmUserId ?: room.roomId))
                .setImportant(true)
                .build()
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_call_notification)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, servicePending(ACTION_HANG_UP)))
                .setContentIntent(openCall(this, room.roomId))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentText(
                    getString(if (connected) R.string.call_ongoing else R.string.call_connecting_notification)
                ).addAction(
                    NotificationCompat.Action
                        .Builder(
                            if (muted) R.drawable.ic_mic_off_notification else R.drawable.ic_mic_notification,
                            getString(if (muted) R.string.call_unmute_action else R.string.call_mute_action),
                            servicePending(ACTION_MUTE),
                        ).build(),
                )
        session.connectedAt?.takeIf { connected }?.let {
            builder
                .setWhen(
                    it
                ).setUsesChronometer(true)
                .setShowWhen(true)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is asked for at startup; without it this does nothing
    private fun NotificationManagerCompat.notifySafely(notification: Notification) {
        runCatching { notify(NOTIFICATION_ID, notification) }
    }

    private fun promote(notification: Notification) {
        val types = grantedTypes()
        if (types == 0) {
            NotificationManagerCompat.from(this).notifySafely(notification)
            return
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } catch (e: IllegalStateException) {
            // A refused start or a type rule (both IllegalStateExceptions): the call carries on.
            notPromoted(notification, e)
        } catch (e: SecurityException) {
            // A permission revoked since the check.
            notPromoted(notification, e)
        }
    }

    private fun notPromoted(
        notification: Notification,
        cause: Exception,
    ) {
        Log.w(TAG, "Couldn't run in the foreground; the call may stop in the background", cause)
        NotificationManagerCompat.from(this).notifySafely(notification)
    }

    private fun grantedTypes(): Int {
        fun has(permission: String) =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        var types = 0
        if (has(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (has(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        return types
    }

    @SuppressLint("WakelockTimeout") // held exactly while a voice call is at the ear
    private fun holdProximity(wanted: Boolean) {
        if (wanted && proximity == null) {
            val power = getSystemService(PowerManager::class.java)
            proximity =
                power
                    ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
                    ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "xmuks:call-proximity")
                    ?.also { it.acquire() }
        } else if (!wanted) {
            proximity?.takeIf { it.isHeld }?.release()
            proximity = null
        }
    }

    private fun servicePending(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, CallService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stop() {
        holdProximity(false)
        session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        stop()
    }

    override fun onDestroy() {
        holdProximity(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallService"
        private const val CHANNEL = "calls_ongoing"
        private const val NOTIFICATION_ID = 0x0ca11
        private const val ACTION_HANG_UP = "pt.aguiarvieira.xmuks.call.HANG_UP"
        private const val ACTION_MUTE = "pt.aguiarvieira.xmuks.call.MUTE"

        /** The extra on the app's launch intent that opens a room's call screen. */
        const val EXTRA_OPEN_CALL = "pt.aguiarvieira.xmuks.OPEN_CALL"

        /** With [EXTRA_OPEN_CALL]: join with the camera on. */
        const val EXTRA_VIDEO = "pt.aguiarvieira.xmuks.CALL_VIDEO"

        /** With [EXTRA_OPEN_CALL]: this is answering a ring. */
        const val EXTRA_ANSWER = "pt.aguiarvieira.xmuks.CALL_ANSWER"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
        }

        /** Opens the app on [roomId]'s call. */
        fun openCall(
            context: Context,
            roomId: String,
            video: Boolean = false,
            answer: Boolean = false,
        ): PendingIntent {
            val launch =
                (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                    .putExtra(EXTRA_OPEN_CALL, roomId)
                    .putExtra(EXTRA_VIDEO, video)
                    .putExtra(EXTRA_ANSWER, answer)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                (roomId + answer).hashCode(),
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_calls_ongoing),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_calls_ongoing_description)
                    setShowBadge(false)
                },
            )
        }
    }
}
