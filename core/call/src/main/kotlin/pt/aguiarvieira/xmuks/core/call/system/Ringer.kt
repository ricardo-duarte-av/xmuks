package pt.aguiarvieira.xmuks.core.call.system

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.IncomingCall
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.call.R
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.notify.Avatars
import pt.aguiarvieira.xmuks.core.notify.PeopleUris
import javax.inject.Inject

/**
 * The incoming-call notification: CallStyle with Answer and Decline, the ringtone looping on its own
 * channel, and the full-screen ringer over the lock screen (where Android lets us: from 14, only
 * calling apps keep USE_FULL_SCREEN_INTENT; without it this is a heads-up that still rings).
 */
class Ringer(
    private val context: Context,
    private val images: ImageLoader,
    private val rooms: RoomListRepository,
    private val scope: CoroutineScope,
) {
    @Volatile private var showing: String? = null

    /** Rings at once; the avatar (a DM's is the caller's) follows when it has loaded. */
    fun show(call: IncomingCall) {
        showing = call.eventId
        post(call, null)
        scope.launch {
            val url = call.avatarUrl ?: rooms.room(call.roomId).first()?.avatarUrl ?: return@launch
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
            val bitmap =
                images
                    .execute(request)
                    .image
                    ?.asDrawable(context.resources)
                    ?.toBitmap() ?: return@launch
            if (showing == call.eventId) post(call, bitmap)
        }
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is asked for at startup
    private fun post(
        call: IncomingCall,
        avatar: Bitmap?,
    ) {
        ensureChannel(context)
        val person =
            Person
                .Builder()
                .setName(call.callerName)
                .setKey(call.callerId)
                .setUri(PeopleUris.forUser(context, call.callerId))
                .setIcon(Avatars.round(avatar, call.callerName, call.callerId))
                .setImportant(true)
                .build()
        val ringScreen =
            PendingIntent.getActivity(
                context,
                call.eventId.hashCode(),
                Intent(ACTION_RING).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val answer = CallService.openCall(context, call.roomId, video = call.video, answer = true)
        val decline =
            PendingIntent.getBroadcast(
                context,
                call.eventId.hashCode(),
                Intent(context, DeclineReceiver::class.java).putExtra(EXTRA_EVENT, call.eventId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text =
            context.getString(
                when {
                    call.video && call.isDirect -> R.string.call_incoming_video
                    call.isDirect -> R.string.call_incoming_voice
                    call.video -> R.string.call_incoming_video_in
                    else -> R.string.call_incoming_voice_in
                },
                call.roomName,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_call_notification)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer).setIsVideo(call.video))
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(ringScreen, true)
                .setContentIntent(ringScreen)
                .setOngoing(true)
                .setAutoCancel(false)
                // The avatar arriving re-posts it: that mustn't start the ringtone over.
                .setOnlyAlertOnce(true)
                .setTimeoutAfter((call.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
                .build()
        // Rings until answered, declined or timed out, as a phone does.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        showing = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        /** Opens the full-screen ringer (an activity in the call feature answers to it). */
        const val ACTION_RING = "pt.aguiarvieira.xmuks.call.RING"
        internal const val EXTRA_EVENT = "event_id"
        private const val CHANNEL = "calls_incoming"
        private const val NOTIFICATION_ID = 0x0ca12

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            val ringtone =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_calls_incoming),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_calls_incoming_description)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ringtone)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, VIBRATE_ON_MS, VIBRATE_OFF_MS)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }

        private const val VIBRATE_ON_MS = 1_000L
        private const val VIBRATE_OFF_MS = 1_000L
    }
}

/** Decline, from the ringing notification. */
@AndroidEntryPoint
class DeclineReceiver : BroadcastReceiver() {
    @Inject lateinit var incoming: IncomingCalls

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        incoming.decline(intent.getStringExtra(Ringer.EXTRA_EVENT))
    }
}
