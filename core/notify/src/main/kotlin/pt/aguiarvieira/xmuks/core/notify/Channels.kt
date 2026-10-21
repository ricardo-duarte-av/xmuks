package pt.aguiarvieira.xmuks.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The notification channels: direct messages and group messages, each with its gomuks sound
 * (`bright`, `descending`). Every room posts to one of them as a conversation (its shortcut), so
 * changing either one (its vibration, say) changes every room that follows it. A room gets a
 * channel of its own only once it's customized, from Android's conversation settings or from the
 * room's info here: then it keeps its own settings. What the push rules say shouldn't make a sound
 * posts silently.
 */
internal object Channels {
    const val DM = "messages_dm"
    const val GROUP = "messages_group"

    /** No longer used: quiet messages post silently. */
    private const val SILENT = "messages_silent"

    /** Rooms' channels as they used to be made, one for every room that notified (see [prune]). */
    private const val LEGACY_ROOM = "room:"

    /** A room's own channel, made when it's customized from its info. */
    private const val ROOM = "conversation:"

    private const val PREFS = "notify_channels"
    private const val PRUNED = "room_channels_pruned"

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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PRUNED, false)) {
            prune(manager)
            prefs.edit().putBoolean(PRUNED, true).apply()
        }
    }

    /**
     * Where [roomId]'s notification posts: its parent, the DM or group channel. Android hands it to
     * the room's own channel when it has one (the shortcut says which room); that one's name
     * follows the room's.
     */
    fun forRoom(
        context: Context,
        roomId: String,
        name: String,
        direct: Boolean,
    ): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        own(manager, roomId)?.let { channel ->
            channel.name = name
            manager.createNotificationChannel(channel)
        }
        return parentOf(direct)
    }

    /**
     * [roomId]'s own channel, to change its settings apart from the rest: the one it has, or a new
     * one starting from its parent's settings as they are now.
     */
    fun customize(
        context: Context,
        roomId: String,
        name: String,
        direct: Boolean,
    ): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        own(manager, roomId)?.let { return it.id }
        val parent = manager.getNotificationChannel(parentOf(direct))
        // A new ID: re-creating a deleted legacy one would bring back its old settings.
        val channel =
            NotificationChannel(ROOM + roomId, name, parent?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
                setConversationId(parentOf(direct), roomId)
                if (parent != null) {
                    setSound(parent.sound, parent.audioAttributes)
                    enableVibration(parent.shouldVibrate())
                    vibrationPattern = parent.vibrationPattern
                    enableLights(parent.shouldShowLights())
                    lightColor = parent.lightColor
                    setShowBadge(parent.canShowBadge())
                    lockscreenVisibility = parent.lockscreenVisibility
                    description = parent.description
                }
            }
        manager.createNotificationChannel(channel)
        return channel.id
    }

    private fun parentOf(direct: Boolean) = if (direct) DM else GROUP

    private fun own(
        manager: NotificationManager,
        roomId: String,
    ): NotificationChannel? =
        manager.getNotificationChannel(ROOM + roomId) ?: manager.getNotificationChannel(LEGACY_ROOM + roomId)

    /**
     * Once: the legacy per-room channels nobody changed go, so their rooms follow their parent; those
     * customized stay, and keep their rooms' settings.
     */
    private fun prune(manager: NotificationManager) {
        manager.notificationChannels
            .filter { it.id.startsWith(LEGACY_ROOM) }
            .forEach { channel ->
                val parent = channel.parentChannelId?.let(manager::getNotificationChannel) ?: return@forEach
                if (untouched(channel, parent)) manager.deleteNotificationChannel(channel.id)
            }
    }

    /**
     * Nothing about [channel] was changed by the user: it was made from [parent], with the same
     * vibration, lights, badge and lock screen settings, so any difference there is theirs.
     */
    internal fun untouched(
        channel: NotificationChannel,
        parent: NotificationChannel,
    ): Boolean =
        !channel.hasUserSetImportance() &&
            !channel.hasUserSetSound() &&
            !channel.isImportantConversation &&
            !channel.canBypassDnd() &&
            channel.shouldVibrate() == parent.shouldVibrate() &&
            channel.vibrationPattern.contentEquals(parent.vibrationPattern) &&
            channel.shouldShowLights() == parent.shouldShowLights() &&
            channel.canShowBadge() == parent.canShowBadge() &&
            channel.lockscreenVisibility == parent.lockscreenVisibility
}
