package pt.aguiarvieira.xmuks.core.data.timeline

import java.time.LocalDate

/** A sender's profile in one room (its `m.room.member` state). */
data class MemberProfile(
    val displayName: String?,
    val avatarMxc: String?,
)

/** What the timeline renders, in order. */
sealed interface TimelineItem {
    /** Stable across updates, for lazy-list keys and animations. */
    val key: String

    data class Message(
        override val key: String,
        val eventId: String,
        val sender: String,
        val senderName: String,
        val senderAvatarMxc: String?,
        val fromMe: Boolean,
        val timestamp: Long,
        val content: MessageContent,
        val reply: ReplyPreview?,
        val reactions: List<Reaction>,
        val edited: Boolean,
        /** First/last of a run of messages from the same sender (and per-message profile). */
        val firstInGroup: Boolean,
        val lastInGroup: Boolean,
        /** Users whose read receipt sits on this message (not us, not the sender). */
        val readBy: List<String>,
        val sendError: String?,
    ) : TimelineItem

    /** Membership and room-setting changes, shown as a quiet line. */
    data class StateChange(
        override val key: String,
        val actorName: String,
        val change: Change,
        val timestamp: Long,
    ) : TimelineItem

    data class DaySeparator(
        override val key: String,
        val day: LocalDate,
    ) : TimelineItem
}

sealed interface Change {
    data object Joined : Change

    data object Left : Change

    data class Invited(
        val target: String,
    ) : Change

    data class Kicked(
        val target: String,
        val reason: String?,
    ) : Change

    data class Banned(
        val target: String,
        val reason: String?,
    ) : Change

    data class Renamed(
        val from: String?,
        val to: String?,
    ) : Change

    data object ChangedAvatar : Change

    data class RoomName(
        val name: String?,
    ) : Change

    data class RoomTopic(
        val topic: String?,
    ) : Change

    data object RoomAvatar : Change

    data object RoomCreated : Change

    data object EncryptionEnabled : Change
}

sealed interface MessageContent {
    /** m.text / m.notice / m.emote. [html] is gomuks' sanitised HTML, null for plain text. */
    data class Text(
        val body: String,
        val html: String?,
        val kind: TextKind,
        val bigEmoji: Boolean,
    ) : MessageContent

    data class Image(
        val media: Media,
        val caption: String?,
    ) : MessageContent

    data class Video(
        val media: Media,
        val caption: String?,
    ) : MessageContent

    data class Audio(
        val media: Media,
        val durationMs: Long?,
    ) : MessageContent

    data class File(
        val media: Media,
        val name: String,
    ) : MessageContent

    data class Sticker(
        val media: Media,
        val body: String,
    ) : MessageContent

    data class Location(
        val body: String,
        val geoUri: String?,
    ) : MessageContent

    data object Redacted : MessageContent

    data class Undecryptable(
        val reason: String?,
    ) : MessageContent

    data class Unsupported(
        val type: String,
        val body: String?,
    ) : MessageContent
}

enum class TextKind { Text, Notice, Emote }

/** An `mxc://` media reference with what the event says about it. */
data class Media(
    val mxc: String,
    val encrypted: Boolean,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val blurhash: String?,
    val thumbnailMxc: String?,
    val thumbnailEncrypted: Boolean,
)

data class ReplyPreview(
    val eventId: String,
    /** Null when the replied-to event isn't loaded (yet). */
    val senderName: String?,
    val text: String?,
)

data class Reaction(
    val key: String,
    val count: Int,
    val mine: Boolean,
) {
    /** Custom emoji (image) reactions use an mxc URI as their key. */
    val isImage: Boolean get() = key.startsWith("mxc://")
}
