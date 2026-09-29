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
        val label: SenderLabel,
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
        /** People whose read receipt sits on this message (not us, not the sender), newest first. */
        val readBy: List<Reader>,
        val sendError: String?,
        /** Our own messages: where the send stands. */
        val sendState: SendState = SendState.Sent,
        /** Set for messages still in our outbox: what resend/discard act on. */
        val localId: String? = null,
        /**
         * For our own text messages: what to put back in the composer to edit it — gomuks' record
         * of what was typed (markdown, `/me` prefix included), else the plain body.
         */
        val editSource: String? = null,
        /** Media still uploading: how far along (0..1). */
        val uploadProgress: Float? = null,
    ) : TimelineItem {
        /** The label as plain text ("profile via sender", or the sender's name). */
        val senderName: String get() = label.text

        /** /me: rendered as an action line, outside the bubbles and their groups. */
        val isEmote: Boolean get() = (content as? MessageContent.Text)?.kind == TextKind.Emote
    }

    /** Membership and room-setting changes, shown as an action line. */
    data class StateChange(
        override val key: String,
        /** Like any event, state changes can be replied to and reacted to. */
        val eventId: String,
        val actor: String,
        val actorName: String,
        val actorAvatarMxc: String?,
        val change: Change,
        val timestamp: Long,
        val reactions: List<Reaction> = emptyList(),
        val readBy: List<Reader> = emptyList(),
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

    /** A member changed their display name and/or avatar (both can change in one event). */
    data class ProfileChanged(
        val oldName: String?,
        val newName: String?,
        val oldAvatar: String?,
        val newAvatar: String?,
        val nameChanged: Boolean,
        val avatarChanged: Boolean,
    ) : Change

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
        /** [html] is gomuks' linkified plain text: its whitespace and line breaks are meant literally. */
        val plainText: Boolean = false,
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
        /** Loudness over time (MSC1767 audio), scaled to 0..1 by the loudest sample; null if none. */
        val waveform: List<Float>? = null,
        /** A voice message (MSC3245), not a music file or the like. */
        val voice: Boolean = false,
        val name: String? = null,
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
    val sender: SenderLabel?,
    val text: String?,
) {
    val senderName: String? get() = sender?.text
}

/**
 * Who a message is attributed to: its sender, or a per-message profile (MSC4144 — bridges and bots
 * speaking for someone) shown as "profile via sender".
 */
data class SenderLabel(
    val senderId: String,
    /** The sender's per-room name (or localpart). */
    val senderName: String,
    /** The per-message profile's own ID and name, when there is one. */
    val profileId: String? = null,
    val profileName: String? = null,
) {
    val text: String get() = profileName?.let { "$it via $senderName" } ?: senderName

    /** The name the message is attributed to: avatar initials, emotes. */
    val shownName: String get() = profileName ?: senderName
}

enum class SendState {
    Sent,

    /** In our outbox, or accepted by gomuks and on its way to the homeserver. */
    Sending,

    /** Nothing was sent (gomuks rejected it, or the homeserver did): can be resent. */
    Failed,

    /** No answer in time: it may or may not have been sent. Only the user can resend it. */
    Unknown,
}

/** Someone whose read receipt is on an event. */
data class Reader(
    val userId: String,
    val name: String,
    val avatarMxc: String?,
    /** When they read it (the receipt's timestamp). */
    val timestamp: Long = 0,
)

data class Reaction(
    val key: String,
    val count: Int,
    val mine: Boolean,
    /** Our own reaction event with this key, when it's loaded (what un-reacting redacts). */
    val myEventId: String? = null,
) {
    /** Custom emoji (image) reactions use an mxc URI as their key. */
    val isImage: Boolean get() = key.startsWith("mxc://")
}
