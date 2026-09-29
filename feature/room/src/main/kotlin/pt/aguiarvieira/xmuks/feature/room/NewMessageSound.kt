package pt.aguiarvieira.xmuks.feature.room

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * A soft blip (`popalert`) when someone else's message arrives in the room on screen — not for
 * what's there when it opens, not for ours, and not when the phone is on silent, vibrate or Do Not
 * Disturb: it sounds like a notification, so it behaves like one.
 */
internal class NewMessageSound(
    private val context: Context,
    scope: CoroutineScope,
    /** Newest first. */
    items: Flow<List<TimelineItem>?>,
    /** Only while the room is actually on screen. */
    private val onScreen: () -> Boolean,
) {
    private val pool =
        SoundPool
            .Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    private val sound = pool.load(context, R.raw.popalert, 1)

    init {
        scope.launch {
            var seen: String? = null
            items
                .filterNotNull()
                .map { list ->
                    list.firstOrNull { it is TimelineItem.Message && !it.fromMe && it.eventId.startsWith("$") }
                }.distinctUntilChanged()
                .collect { newest ->
                    val id = (newest as? TimelineItem.Message)?.eventId ?: return@collect
                    // The first newest message is what the room opened with: only later ones blip.
                    val arrived = seen != null && id != seen
                    seen = id
                    if (arrived && onScreen() && audible()) pool.play(sound, VOLUME, VOLUME, 0, 0, 1f)
                }
        }
    }

    private fun audible(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun release() = pool.release()

    private companion object {
        const val VOLUME = 0.6f
    }
}
