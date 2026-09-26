package pt.aguiarvieira.xmuks.core.data.rooms

/** How loud a room's (or a space's) unread state is, most important first. */
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

data class SpaceSummary(
    val roomId: String,
    val name: String,
    val avatarUrl: String?,
    val rooms: Int,
    val unread: Unread,
)
