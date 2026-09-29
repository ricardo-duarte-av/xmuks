package pt.aguiarvieira.xmuks.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The notification channels: direct messages and group messages, each with its gomuks sound
 * (`bright`, `descending`), and a quiet one for what the push rules say shouldn't make a sound.
 * People can change each in Android's settings (sound, importance), and per conversation too.
 */
internal object Channels {
    const val DM = "messages_dm"
    const val GROUP = "messages_group"
    const val SILENT = "messages_silent"

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
                NotificationChannel(
                    SILENT,
                    context.getString(R.string.channel_silent),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_silent_description)
                    setSound(null, null)
                },
            ),
        )
    }
}
