package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.protocol.Event

/**
 * How well a bridged network takes something: mautrix's capability levels (-2 to 2). A level a
 * bridge leaves out is [Unsupported], as mautrix omits empty fields.
 */
enum class Support {
    /** The bridge refuses it with an error. */
    Rejected,

    /** The bridge accepts it but it never arrives (a caption on audio, say). */
    Dropped,
    Unsupported,

    /** It arrives converted (a PNG re-encoded, headers turned bold). */
    Partial,
    Full,
    ;

    val works: Boolean get() = this >= Partial

    companion object {
        @Suppress("MagicNumber") // mautrix's numbering
        fun of(level: Long?): Support =
            when {
                level == null -> Unsupported
                level <= -2 -> Rejected
                level == -1L -> Dropped
                level == 0L -> Unsupported
                level == 1L -> Partial
                else -> Full
            }
    }
}

/** What a network takes for one kind of file (`m.image`, `m.audio`…). */
data class FileFeatures(
    /** By MIME type; `type/*` and `*/*` cover the rest. */
    val mimeTypes: Map<String, Support>,
    /** In bytes; null when there's no limit. */
    val maxSize: Long?,
    val caption: Support,
    val maxCaptionLength: Int?,
) {
    /** How [mime] fares: an exact match (parameters too), then without parameters, `type/*`, `*/*`. */
    fun support(mime: String): Support {
        val full = mime.trim().lowercase()
        val bare = full.substringBefore(';').trim()
        return mimeTypes[full]
            ?: mimeTypes[bare]
            ?: mimeTypes["${bare.substringBefore('/')}/*"]
            ?: mimeTypes["*/*"]
            ?: Support.Unsupported
    }

    internal fun stricter(other: FileFeatures) =
        FileFeatures(
            mimeTypes = (mimeTypes.keys + other.mimeTypes.keys).associateWith { minOf(support(it), other.support(it)) },
            maxSize = smaller(maxSize, other.maxSize),
            caption = minOf(caption, other.caption),
            maxCaptionLength = smaller(maxCaptionLength, other.maxCaptionLength),
        )
}

/** Something the user can do that a network may not allow. */
enum class Action { Edit, Delete, React, Reply, Thread, Poll, Location }

/** Why a network won't take something: what dims an option and what tapping it says. */
sealed interface Limit {
    /** The network can't do [action] at all. */
    data class Unsupported(
        val action: Action,
    ) : Limit

    /** Only within [maxAgeSec] seconds of sending. */
    data class TooOld(
        val action: Action,
        val maxAgeSec: Long,
    ) : Limit

    /** Not this reaction (custom emoji, or one outside the network's set). */
    data object ReactionNotAllowed : Limit

    /** Text longer than [max] characters. */
    data class TextTooLong(
        val max: Int,
    ) : Limit

    /** A poll with more than [max] options. */
    data class TooManyOptions(
        val max: Int,
    ) : Limit

    /** A file the network won't take. */
    data class Media(
        val issue: MediaIssue,
    ) : Limit

    /** Not the network: the room is only being previewed, and nothing is sent from a preview. */
    data object Preview : Limit
}

/** What a network would make of a file about to be sent. */
sealed interface MediaIssue {
    /** Whether sending is pointless: the file wouldn't arrive. */
    val blocks: Boolean

    /** Not this kind of message at all ([msgtype]). */
    data class KindUnsupported(
        val msgtype: String,
    ) : MediaIssue {
        override val blocks = true
    }

    /** Not this format. */
    data class FormatUnsupported(
        val mime: String,
    ) : MediaIssue {
        override val blocks = true
    }

    /** Bigger than [max] bytes. */
    data class TooBig(
        val max: Long,
    ) : MediaIssue {
        override val blocks = true
    }

    /** The caption won't arrive. */
    data object CaptionDropped : MediaIssue {
        override val blocks = false
    }

    /** The caption is longer than [max] characters. */
    data class CaptionTooLong(
        val max: Int,
    ) : MediaIssue {
        override val blocks = false
    }

    /** It arrives converted to another format. */
    data object Converted : MediaIssue {
        override val blocks = false
    }
}

/**
 * What the network(s) a room is bridged to can do, from the bridges' `com.beeper.room_features`
 * state (one per bridge, merged to the strictest). Limits are null when there are none.
 */
data class RoomFeatures(
    val edit: Support,
    val editMaxAgeSec: Long?,
    val delete: Support,
    val deleteMaxAgeSec: Long?,
    val reaction: Support,
    /** How many reactions each person may leave on a message. */
    val reactionCount: Int?,
    /** The only reactions allowed; null when any is. */
    val allowedReactions: Set<String>?,
    val customEmojiReactions: Boolean,
    val reply: Support,
    val thread: Support,
    val poll: Support,
    val pollMaxOptions: Int?,
    val pollOptionMaxLength: Int?,
    val locationMessage: Support,
    val maxTextLength: Int?,
    /** By formatting feature (`bold`, `code_block`, `headers`…). */
    val formatting: Map<String, Support>,
    /** By msgtype. */
    val files: Map<String, FileFeatures>,
    /** The disappearing-message timers offered, in milliseconds. */
    val disappearingTimers: List<Long>,
    val typingNotifications: Boolean,
    val readReceipts: Boolean,
) {
    /** Why a message sent at [sentAt] can't be edited on the network at [now], if it can't. */
    fun editBlock(
        sentAt: Long,
        now: Long,
    ): Limit? = timed(Action.Edit, edit, editMaxAgeSec, sentAt, now)

    /** Why deleting a message sent at [sentAt] wouldn't reach the network at [now]: the Matrix copy still goes. */
    fun deleteBlock(
        sentAt: Long,
        now: Long,
    ): Limit? = timed(Action.Delete, delete, deleteMaxAgeSec, sentAt, now)

    /** Why reacting with [key] (a custom emoji when [custom]) won't do, if it won't. */
    fun reactionBlock(
        key: String,
        custom: Boolean,
    ): Limit? =
        when {
            !reaction.works -> Limit.Unsupported(Action.React)
            custom && !customEmojiReactions -> Limit.ReactionNotAllowed
            allowedReactions != null && key !in allowedReactions -> Limit.ReactionNotAllowed
            else -> null
        }

    fun replyBlock(): Limit? = Limit.Unsupported(Action.Reply).takeUnless { reply.works }

    fun threadBlock(): Limit? = Limit.Unsupported(Action.Thread).takeUnless { thread.works }

    fun pollBlock(): Limit? = Limit.Unsupported(Action.Poll).takeUnless { poll.works }

    fun locationBlock(): Limit? = Limit.Unsupported(Action.Location).takeUnless { locationMessage.works }

    /** Why a text of [length] characters won't go, if it won't. */
    fun textBlock(length: Int): Limit? = maxTextLength?.takeIf { length > it }?.let(Limit::TextTooLong)

    /** Whether the network can take voice messages at all. */
    val voiceMessages: Boolean get() = files[VOICE]?.support(VOICE_MIME)?.works == true

    /**
     * What the network would make of a [msgtype] file of [mime] and [size] bytes (negative when
     * unknown) with a caption of [captionLength] characters.
     */
    fun checkMedia(
        msgtype: String,
        mime: String?,
        size: Long,
        captionLength: Int = 0,
    ): List<MediaIssue> {
        val file = files[msgtype] ?: return listOf(MediaIssue.KindUnsupported(msgtype))
        return buildList {
            val support = mime?.let(file::support)
            when {
                support == null -> Unit
                !support.works -> add(MediaIssue.FormatUnsupported(mime))
                support == Support.Partial -> add(MediaIssue.Converted)
            }
            if (file.maxSize != null && size > file.maxSize) add(MediaIssue.TooBig(file.maxSize))
            if (captionLength > 0) {
                when {
                    !file.caption.works -> {
                        add(MediaIssue.CaptionDropped)
                    }

                    file.maxCaptionLength != null && captionLength > file.maxCaptionLength -> {
                        add(MediaIssue.CaptionTooLong(file.maxCaptionLength))
                    }
                }
            }
        }
    }

    private fun timed(
        action: Action,
        support: Support,
        maxAgeSec: Long?,
        sentAt: Long,
        now: Long,
    ): Limit? =
        when {
            !support.works -> Limit.Unsupported(action)
            maxAgeSec != null && now - sentAt > maxAgeSec * MILLIS -> Limit.TooOld(action, maxAgeSec)
            else -> null
        }

    internal fun stricter(other: RoomFeatures) =
        RoomFeatures(
            edit = minOf(edit, other.edit),
            editMaxAgeSec = smaller(editMaxAgeSec, other.editMaxAgeSec),
            delete = minOf(delete, other.delete),
            deleteMaxAgeSec = smaller(deleteMaxAgeSec, other.deleteMaxAgeSec),
            reaction = minOf(reaction, other.reaction),
            reactionCount = smaller(reactionCount, other.reactionCount),
            allowedReactions =
                if (allowedReactions == null || other.allowedReactions == null) {
                    allowedReactions ?: other.allowedReactions
                } else {
                    allowedReactions intersect other.allowedReactions
                },
            customEmojiReactions = customEmojiReactions && other.customEmojiReactions,
            reply = minOf(reply, other.reply),
            thread = minOf(thread, other.thread),
            poll = minOf(poll, other.poll),
            pollMaxOptions = smaller(pollMaxOptions, other.pollMaxOptions),
            pollOptionMaxLength = smaller(pollOptionMaxLength, other.pollOptionMaxLength),
            locationMessage = minOf(locationMessage, other.locationMessage),
            maxTextLength = smaller(maxTextLength, other.maxTextLength),
            formatting =
                (formatting.keys + other.formatting.keys).associateWith {
                    minOf(formatting[it] ?: Support.Unsupported, other.formatting[it] ?: Support.Unsupported)
                },
            files =
                (files.keys intersect other.files.keys).associateWith {
                    files.getValue(it).stricter(other.files.getValue(it))
                },
            disappearingTimers = disappearingTimers.filter { it in other.disappearingTimers },
            typingNotifications = typingNotifications && other.typingNotifications,
            readReceipts = readReceipts && other.readReceipts,
        )

    companion object {
        const val TYPE = "com.beeper.room_features"
        const val IMAGE = "m.image"
        const val VIDEO = "m.video"
        const val AUDIO = "m.audio"
        const val FILE = "m.file"
        const val STICKER = "m.sticker"
        const val GIF = "fi.mau.gif"
        const val VOICE = "org.matrix.msc3245.voice"

        /** What our voice recordings are (Ogg/Opus), as bridges list it. */
        const val VOICE_MIME = "audio/ogg; codecs=opus"

        private const val MILLIS = 1000L

        /** The msgtype a file of [kind] goes out as; [voice] for a voice message. */
        fun msgtypeOf(
            kind: MediaKind,
            voice: Boolean = false,
        ): String =
            when {
                voice -> VOICE
                kind == MediaKind.Image -> IMAGE
                kind == MediaKind.Video -> VIDEO
                kind == MediaKind.Audio -> AUDIO
                else -> FILE
            }
    }
}

/** What the networks in [state] can do; null when the room isn't bridged (or its bridges don't say). */
fun roomFeaturesOf(state: List<Event>): RoomFeatures? =
    state
        .filter { it.effectiveType == RoomFeatures.TYPE && it.redactedBy == null }
        .map { parseFeatures(it.effectiveContent) }
        .reduceOrNull(RoomFeatures::stricter)

internal fun parseFeatures(content: JsonObject): RoomFeatures =
    RoomFeatures(
        edit = content.level("edit"),
        editMaxAgeSec = content.limit("edit_max_age"),
        delete = content.level("delete"),
        deleteMaxAgeSec = content.limit("delete_max_age"),
        reaction = content.level("reaction"),
        reactionCount = content.limit("reaction_count")?.toInt(),
        allowedReactions = content.strings("allowed_reactions")?.toSet(),
        customEmojiReactions = content.bool("custom_emoji_reactions") == true,
        reply = content.level("reply"),
        thread = content.level("thread"),
        poll = content.level("poll"),
        pollMaxOptions = content.limit("poll_max_options")?.toInt(),
        pollOptionMaxLength = content.limit("poll_option_max_length")?.toInt(),
        locationMessage = content.level("location_message"),
        maxTextLength = content.limit("max_text_length")?.toInt(),
        formatting = content.obj("formatting")?.levels().orEmpty(),
        files =
            content
                .obj("file")
                .orEmpty()
                .mapNotNull { (msgtype, value) ->
                    val file = value as? JsonObject ?: return@mapNotNull null
                    msgtype to
                        FileFeatures(
                            mimeTypes =
                                file
                                    .obj("mime_types")
                                    ?.levels()
                                    ?.mapKeys { it.key.lowercase() }
                                    .orEmpty(),
                            maxSize = file.limit("max_size"),
                            caption = file.level("caption"),
                            maxCaptionLength = file.limit("max_caption_length")?.toInt(),
                        )
                }.toMap(),
        disappearingTimers =
            (content.obj("disappearing_timer")?.get("timers") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                .orEmpty(),
        typingNotifications = content.bool("typing_notifications") == true,
        readReceipts = content.bool("read_receipts") == true,
    )

private fun JsonObject.level(key: String) = Support.of((get(key) as? JsonPrimitive)?.longOrNull)

private fun JsonObject.levels() = mapValues { (_, value) -> Support.of((value as? JsonPrimitive)?.longOrNull) }

/** A positive number, or null: 0 or absent means no limit. */
private fun JsonObject.limit(key: String) = long(key)?.takeIf { it > 0 }

private fun JsonObject.strings(key: String) =
    (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }

private fun <T : Comparable<T>> smaller(
    a: T?,
    b: T?,
): T? = if (a == null || b == null) a ?: b else minOf(a, b)
