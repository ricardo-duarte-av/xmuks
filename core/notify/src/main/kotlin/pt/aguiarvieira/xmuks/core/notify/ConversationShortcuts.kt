package pt.aguiarvieira.xmuks.core.notify

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PersistableBundle
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.delay

/**
 * A room's long-lived shortcut as a push makes it (the conversation its notification belongs
 * to, and whatever is pinned for it): made with the push's avatar, which it keeps in its extras
 * with the push's time ([ShortcutStamps]), so the room list, catching up, doesn't put back an
 * older one.
 */
internal class ConversationShortcuts(
    private val context: Context,
) {
    /**
     * Publishes [key]'s shortcut for [latest]: [avatar] loaded from [avatarMxc] (null when it
     * can't be told). A new icon is told again once it's written, or the launcher keeps the old one.
     */
    suspend fun publish(
        key: String,
        latest: PushMessage,
        avatar: Bitmap?,
        avatarMxc: String?,
        people: Collection<Person>,
        intent: Intent,
    ) {
        // The avatar it's made with, unless it wouldn't load: the room list then has its say.
        val stamp =
            avatarMxc
                ?.takeIf { latest.roomAvatar == null || avatar != null }
                ?.let { ShortcutStamps.of(it, latest.timestamp) }
        val had =
            runCatching {
                ShortcutManagerCompat.getShortcuts(context, KINDS).firstOrNull { it.id == key }?.extras
            }.getOrNull()
        val icon = Avatars.adaptive(avatar, latest.roomName, latest.roomId)
        val shortcut = shortcut(key, latest.roomName, people, intent, stamp, icon)
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }.onFailure { return }
        val made = ShortcutStamps.avatarOf(stamp)
        if (made != null && ShortcutStamps.avatarOf(had) == made) return
        delay(ShortcutStamps.ICON_WRITTEN_MS)
        runCatching {
            ShortcutManagerCompat.updateShortcuts(
                context,
                listOf(shortcut(key, latest.roomName, people, intent, stamp, icon = null)),
            )
        }
    }

    /** A builder's info is the one it built: each one is built anew. Without an [icon], the one there is stays. */
    private fun shortcut(
        key: String,
        name: String,
        people: Collection<Person>,
        intent: Intent,
        stamp: PersistableBundle?,
        icon: IconCompat?,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, key)
            .setShortLabel(name)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(key))
            .setIntent(intent)
            .setPersons(people.toTypedArray())
            .setCategories(setOf(RoomNotifier.SHORTCUT_CATEGORY))
            .apply {
                icon?.let(::setIcon)
                stamp?.let(::setExtras)
            }.build()

    private companion object {
        const val KINDS =
            ShortcutManagerCompat.FLAG_MATCH_PINNED or
                ShortcutManagerCompat.FLAG_MATCH_CACHED or
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
    }
}
