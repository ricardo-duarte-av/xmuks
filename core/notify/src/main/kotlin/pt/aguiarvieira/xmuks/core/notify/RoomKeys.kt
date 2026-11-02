package pt.aguiarvieira.xmuks.core.notify

/**
 * A room as the system knows it (notification tag, shortcut and conversation ID, channel): two
 * accounts can be in the same room, so beside the first account's plain room ID (as it always
 * was, keeping its shortcuts and settings), another's is `account/roomId`. Room IDs start with
 * `!`, account IDs never do, so the two can't be confused.
 */
object RoomKeys {
    /** On intents we make (a room's, a call's): the account it's for. */
    const val EXTRA_ACCOUNT = "pt.aguiarvieira.xmuks.ACCOUNT"

    /** With [EXTRA_ACCOUNT] (an account's own shortcut): show its room list. */
    const val EXTRA_HOME = "pt.aguiarvieira.xmuks.HOME"

    /** [roomId]'s key for [account] (null: the first account's, the plain room ID). */
    fun of(
        account: String?,
        roomId: String,
    ): String = if (account == null) roomId else "$account$SEPARATOR$roomId"

    /** The room a key is for. */
    fun roomOf(key: String): String = if (isPlain(key)) key else key.substringAfter(SEPARATOR)

    /** The account a key is for; null for the first account's (a plain room ID). */
    fun accountOf(key: String): String? = if (isPlain(key)) null else key.substringBefore(SEPARATOR)

    private fun isPlain(key: String) = key.startsWith(ROOM_SIGIL) || SEPARATOR !in key

    private const val SEPARATOR = '/'
    private const val ROOM_SIGIL = '!'
}
