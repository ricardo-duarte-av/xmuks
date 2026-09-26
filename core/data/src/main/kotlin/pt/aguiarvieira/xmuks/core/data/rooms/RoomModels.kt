package pt.aguiarvieira.xmuks.core.data.rooms

/**
 * How loud a room's unread state is. For aggregates (a space, a tab) the fields count *rooms*:
 * rooms with anything unread, rooms needing attention (notifying or mentioning), rooms mentioning us.
 */
data class Unread(
    val messages: Int = 0,
    val notifications: Int = 0,
    val highlights: Int = 0,
    val marked: Boolean = false,
) {
    val any: Boolean get() = messages > 0 || notifications > 0 || highlights > 0 || marked
}

data class RoomSummary(
    val roomId: String,
    val name: String,
    /** Ready-to-load avatar thumbnail URL, or null for the initials fallback. */
    val avatarUrl: String?,
    val isDirect: Boolean,
    val encrypted: Boolean,
    /** Who sent the preview: a display name, "You" is signalled by [previewFromMe]. */
    val previewSender: String?,
    val previewFromMe: Boolean,
    val preview: Preview,
    /** When the room last had activity (gomuks' sorting timestamp). */
    val timestamp: Long,
    val unread: Unread,
)

/** What the room list shows under the room name. */
sealed interface Preview {
    data class Text(
        val text: String,
    ) : Preview

    data object Sticker : Preview

    data object Encrypted : Preview

    data object None : Preview
}

/** Badges for the bottom bar. */
data class TabBadges(
    val chats: Unread = Unread(),
    val dms: Unread = Unread(),
    val spaces: Unread = Unread(),
)

data class SpaceSummary(
    val roomId: String,
    val name: String,
    val avatarUrl: String?,
    val rooms: Int,
    val unread: Unread,
)

/** The logged-in user as the homeserver describes them. */
data class OwnProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
)
