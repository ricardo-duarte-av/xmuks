package pt.aguiarvieira.xmuks.core.notify

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Android's notification settings for [roomId] alone (sound, vibration, priority…): the room gets
 * its own channel, from its parent's settings as they are now, if it hasn't one yet.
 */
fun roomNotificationSettings(
    context: Context,
    roomId: String,
    name: String,
    direct: Boolean,
): Intent {
    Channels.ensure(context)
    val channel = Channels.customize(context, roomId, name, direct)
    return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
}
