package pt.aguiarvieira.xmuks.core.notify

import android.os.PersistableBundle

/**
 * What a room's shortcut was made from, kept in its extras: the avatar (an `mxc://`, empty for
 * none) and how recent a view of the room that was (gomuks' timestamp: the push's message, the
 * room list's sorting timestamp). Both a push and the room list update a room's shortcut; the
 * room list, read from our cache as a push wakes the app, can be older than the push, and must
 * not put back what the push just replaced.
 */
object ShortcutStamps {
    /** The avatar a shortcut was made with (a room's mxc, an account's URL); empty for none. */
    const val AVATAR = "xmuks.avatar"

    /** How recent the view of the room it was made from is (ms, gomuks' clock); absent: unknown. */
    const val AS_OF = "xmuks.as_of"

    /**
     * Updating a shortcut's icon, the system writes it out in the background and tells the
     * launcher before it's done: the launcher loads (and keeps) the icon there was. Told again
     * this much later, by an update that leaves the icon be, it loads the new one.
     */
    const val ICON_WRITTEN_MS = 1_000L

    fun of(
        avatar: String?,
        asOf: Long?,
    ) = PersistableBundle().apply {
        putString(AVATAR, avatar.orEmpty())
        asOf?.let { putLong(AS_OF, it) }
    }

    fun avatarOf(extras: PersistableBundle?): String? = extras?.getString(AVATAR)

    fun asOfOf(extras: PersistableBundle?): Long = extras?.getLong(AS_OF, 0L) ?: 0L
}
