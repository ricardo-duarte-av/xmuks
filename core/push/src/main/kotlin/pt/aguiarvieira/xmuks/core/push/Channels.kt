package pt.aguiarvieira.xmuks.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The notification channels: direct messages and group messages, each with its gomuks sound
 * (`bright`, `descending`), and under them one channel per room — a conversation channel, so every
 * room is listed in Android's conversation settings and can be given its own sound, importance or
 * priority. What the push rules say shouldn't make a sound posts to the same channel, silently.
 */
internal object Channels {
    const val DM = "messages_dm"
    const val GROUP = "messages_group"

    /** No longer used: quiet messages post silently to their room's own channel. */
    private const val SILENT = "messages_silent"
    private const val CONVERSATION = "room:"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val audio =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        fun sound(res: Int) = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$res")
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    DM,
                    context.getString(R.string.channel_dm),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_dm_description)
                    setSound(sound(R.raw.bright), audio)
                },
                NotificationChannel(
                    GROUP,
                    context.getString(R.string.channel_group),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_group_description)
                    setSound(sound(R.raw.descending), audio)
                },
            ),
        )
        manager.deleteNotificationChannel(SILENT)
    }

    /**
     * [roomId]'s own conversation channel under the DM or group channel, created (as its parent is)
     * the first time the room notifies; after that only its name follows the room's. Android keeps
     * whatever the user changed on it.
     */
    fun conversation(
        context: Context,
        roomId: String,
        name: String,
        direct: Boolean,
    ): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = CONVERSATION + roomId
        val parent = manager.getNotificationChannel(if (direct) DM else GROUP)
        val channel =
            manager.getNotificationChannel(id)?.apply { this.name = name }
                ?: NotificationChannel(id, name, parent?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
                    setConversationId(parent?.id ?: if (direct) DM else GROUP, roomId)
                    setSound(parent?.sound, parent?.audioAttributes)
                    description = parent?.description
                }
        manager.createNotificationChannel(channel)
        return id
    }
}
