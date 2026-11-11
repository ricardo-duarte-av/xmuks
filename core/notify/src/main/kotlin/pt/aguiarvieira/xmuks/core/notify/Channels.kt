package pt.aguiarvieira.xmuks.core.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
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
 *
 * Each account has its own two, in a channel group named after it (its user ID), so they're told
 * apart, and set apart, in Android's settings: the first account's keep their plain IDs (and the
 * settings made to them), another's are `account/messages_dm` and so on ([RoomKeys]). A room's own
 * channel goes in its account's group.
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

    /** An account's channel group: this, then the account (or [FIRST]). */
    private const val ACCOUNT_GROUP = "account:"
    private const val FIRST = "first"

    private const val PREFS = "notify_channels"
    private const val PRUNED = "room_channels_pruned"

    /**
     * [account]'s channels (null: the first account's), in a group named [label] once that's known
     * (pushes say whose they are); before, they're made without one, and join it then.
     */
    fun ensure(
        context: Context,
        account: String? = null,
        label: String? = null,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val groupId = label?.let { groupOf(account) }
        if (groupId != null) manager.createNotificationChannelGroup(NotificationChannelGroup(groupId, label))
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
                    parentOf(account, direct = true),
                    context.getString(R.string.channel_dm),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_dm_description)
                    setSound(sound(R.raw.bright), audio)
                    group = groupId
                },
                NotificationChannel(
                    parentOf(account, direct = false),
                    context.getString(R.string.channel_group),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_group_description)
                    setSound(sound(R.raw.descending), audio)
                    group = groupId
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
     * Where [roomId]'s notification posts ([roomId] is a room key, [RoomKeys]): its account's DM or
     * group channel. Android hands it to the room's own channel when it has one (the shortcut says
     * which room); that one's name follows the room's, and it joins its account's group.
     */
    fun forRoom(
        context: Context,
        roomId: String,
        name: String,
        direct: Boolean,
    ): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val parent = parentOf(RoomKeys.accountOf(roomId), direct)
        own(manager, roomId)?.let { channel ->
            channel.name = name
            if (channel.group == null) channel.group = manager.getNotificationChannel(parent)?.group
            manager.createNotificationChannel(channel)
        }
        return parent
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
        val parentId = parentOf(RoomKeys.accountOf(roomId), direct)
        val parent = manager.getNotificationChannel(parentId)
        // A new ID: re-creating a deleted legacy one would bring back its old settings.
        val channel =
            NotificationChannel(ROOM + roomId, name, parent?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
                setConversationId(parentId, roomId)
                if (parent != null) {
                    group = parent.group
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

    /** [account]'s DM or group channel (null: the first account's, [DM] or [GROUP]). */
    internal fun parentOf(
        account: String?,
        direct: Boolean,
    ) = RoomKeys.of(account, if (direct) DM else GROUP)

    private fun groupOf(account: String?) = ACCOUNT_GROUP + (account ?: FIRST)

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
