package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.Receipt
import java.time.Instant
import java.time.ZoneId

/**
 * Turns a [TimelineSnapshot] into what the screen shows. Pure: same input, same output.
 *
 * Sender names/avatars, in order: the message's own per-message profile (MSC4144), the sender's
 * `m.room.member` in this room ([profiles], newest member events in the timeline winning), then
 * the Matrix ID's localpart.
 */
class TimelineItemBuilder(
    internal val me: String?,
    private val options: TimelineOptions = TimelineOptions(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun build(
        snapshot: TimelineSnapshot,
        profiles: Map<String, MemberProfile>,
    ): List<TimelineItem> {
        val byEventId = snapshot.eventsByRowId.values.associateBy { it.eventId }
        val members = profiles + membersFromTimeline(snapshot.events)
        val myReactions = myReactions(snapshot.eventsByRowId.values)
        val readers = if (options.showReadReceipts) readersByEvent(snapshot) { it.isShown() } else emptyMap()

        val out = ArrayList<TimelineItem>(snapshot.events.size + DAY_SEPARATOR_SLACK)
        var previous: Event? = null
        var previousGroup: String? = null
        // What's shown: left out by preference (or unrenderable) is as if it weren't there, so the
        // messages around it still group, and a day of only such events gets no separator.
        val shown =
            snapshot.events.mapNotNull { event ->
                itemFor(event, snapshot, byEventId, members, myReactions, readers)
                    ?.takeIf(options::shows)
                    ?.let { event to it }
            }
        for ((event, item) in shown) {
            val day = Instant.ofEpochMilli(event.timestamp).atZone(zone).toLocalDate()
            val previousDay = previous?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            if (day != previousDay && options.showDateSeparators) out += TimelineItem.DaySeparator("day:$day", day)
            // Messages from the same sender (and profile) within minutes of each other form a
            // group; emotes and state changes stand alone and end the group around them.
            val group = (item as? TimelineItem.Message)?.takeUnless { it.isEmote }?.let { groupKey(event, it) }
            val gap = previous?.let { event.timestamp - it.timestamp } ?: Long.MAX_VALUE
            val continues = group != null && group == previousGroup && day == previousDay && gap < GROUP_GAP_MS
            out.appendGrouped(item, continues)
            previousGroup = group
            previous = event
        }
        return out
    }

    private fun itemFor(
        event: Event,
        snapshot: TimelineSnapshot,
        byEventId: Map<String, Event>,
        members: Map<String, MemberProfile>,
        myReactions: Map<String, Map<String, String>>,
        readers: Map<String, List<Receipt>>,
    ): TimelineItem? {
        val shown =
            when {
                !event.isShown() -> null
                event.stateKey != null -> stateChange(event, members, myReactions, readers)
                else -> message(event, snapshot, byEventId, members, myReactions, readers)
            }
        // Whatever has nothing of its own to show (a reaction, an edit, a state change that changed
        // nothing, a type we don't render) is a hidden event.
        return shown ?: hidden(event, members)
    }

    private fun hidden(
        event: Event,
        members: Map<String, MemberProfile>,
    ) = TimelineItem.Hidden(
        key = "hidden:${event.eventId}",
        eventId = event.eventId,
        sender = event.sender,
        senderName = members[event.sender]?.displayName ?: localpart(event.sender),
        type = event.effectiveType,
        timestamp = event.timestamp,
    )

    // --- visibility ---------------------------------------------------------------------------

    private fun Event.isShown(): Boolean =
        when {
            relationType == "m.replace" -> false

            // edits are folded into the original
            effectiveType in HIDDEN_TYPES -> false

            stateKey != null -> effectiveType in STATE_TYPES

            else -> effectiveType in MESSAGE_TYPES
        }

    // --- messages -------------------------------------------------------------------------------

    private fun message(
        event: Event,
        snapshot: TimelineSnapshot,
        byEventId: Map<String, Event>,
        members: Map<String, MemberProfile>,
        myReactions: Map<String, Map<String, String>>,
        readers: Map<String, List<Receipt>>,
    ): TimelineItem.Message {
        val edit = event.lastEditRowId?.let(snapshot.eventsByRowId::get)
        val content = (edit?.effectiveContent?.obj("m.new_content")) ?: event.effectiveContent
        val local = (edit ?: event).localContent
        val html = htmlOf(local?.sanitizedHtml, content)
        val perMessage = content.obj(PER_MESSAGE_PROFILE) ?: content.obj(PER_MESSAGE_PROFILE_STABLE)
        val profile = members[event.sender]
        return TimelineItem.Message(
            key = "e:${event.rowId}",
            eventId = event.eventId,
            sender = event.sender,
            label = senderLabel(perMessage, event.sender, members),
            senderAvatarMxc = perMessage?.str("avatar_url")?.takeIf { it.startsWith("mxc://") } ?: profile?.avatarMxc,
            fromMe = event.sender == me,
            timestamp = event.timestamp,
            content = contentOf(event, content, html, local?.bigEmoji == true).withPlainText(local),
            reply = replyOf(content, event, byEventId, members),
            reactions = reactionsOf(event, myReactions),
            edited = edit != null,
            firstInGroup = true,
            lastInGroup = true,
            readBy = readers[event.eventId].orEmpty().toReaders(me, event.sender, members),
            sendError = event.sendError?.takeIf { it.isNotBlank() && it != NOT_SENT },
            sendState = event.sendState(),
            editSource = editSourceOf(event, local, content),
        )
    }

    private fun contentOf(
        event: Event,
        content: JsonObject,
        html: String?,
        bigEmoji: Boolean,
    ): MessageContent {
        if (event.redactedBy != null) return MessageContent.Redacted
        if (event.type == "m.room.encrypted" &&
            event.decrypted == null
        ) {
            return MessageContent.Undecryptable(event.decryptionError)
        }
        val body = content.str("body").orEmpty()
        if (event.effectiveType == "m.sticker") {
            return media(content)?.let { MessageContent.Sticker(it, body) }
                ?: MessageContent.Unsupported("m.sticker", body)
        }
        return byMsgtype(content, body, html, bigEmoji)
            ?: MessageContent.Unsupported(content.str("msgtype") ?: event.effectiveType, body)
    }

    private fun byMsgtype(
        content: JsonObject,
        body: String,
        html: String?,
        bigEmoji: Boolean,
    ): MessageContent? =
        when (val msgtype = content.str("msgtype")) {
            "m.text", null -> {
                MessageContent.Text(body, html, TextKind.Text, bigEmoji)
            }

            "m.notice" -> {
                MessageContent.Text(body, html, TextKind.Notice, bigEmoji)
            }

            "m.emote" -> {
                MessageContent.Text(body, html, TextKind.Emote, bigEmoji)
            }

            "m.image", "m.video", "m.audio", "m.file" -> {
                mediaMessage(msgtype, content, body)
            }

            "m.location" -> {
                MessageContent.Location(body, content.str("geo_uri"))
            }

            // Unknown msgtypes still render their fallback body when they have one.
            else -> {
                if (body.isNotEmpty()) {
                    MessageContent.Text(
                        body,
                        html,
                        TextKind.Text,
                        bigEmoji
                    )
                } else {
                    MessageContent.Unsupported(msgtype, null)
                }
            }
        }

    private fun replyOf(
        content: JsonObject,
        event: Event,
        byEventId: Map<String, Event>,
        members: Map<String, MemberProfile>,
    ): ReplyPreview? {
        val relatesTo = content.obj("m.relates_to") ?: event.content.obj("m.relates_to") ?: return null
        val target = relatesTo.obj("m.in_reply_to")?.str("event_id") ?: return null
        val original = byEventId[target] ?: return ReplyPreview(target, sender = null, text = null)
        val originalProfile =
            original.effectiveContent.obj(PER_MESSAGE_PROFILE)
                ?: original.effectiveContent.obj(PER_MESSAGE_PROFILE_STABLE)
        return ReplyPreview(
            eventId = target,
            sender = senderLabel(originalProfile, original.sender, members),
            text = original.localContent?.previewText ?: original.effectiveContent.str("body"),
        )
    }

    private fun groupKey(
        event: Event,
        item: TimelineItem.Message,
    ): String {
        val profileId =
            event.effectiveContent.obj(PER_MESSAGE_PROFILE)?.str("id")
                ?: event.effectiveContent.obj(PER_MESSAGE_PROFILE_STABLE)?.str("id")
        return "${item.sender}|${profileId.orEmpty()}"
    }

    // --- state ----------------------------------------------------------------------------------

    private fun stateChange(
        event: Event,
        members: Map<String, MemberProfile>,
        myReactions: Map<String, Map<String, String>>,
        readers: Map<String, List<Receipt>>,
    ): TimelineItem.StateChange? {
        val content = event.effectiveContent
        val actor = members[event.sender]?.displayName ?: localpart(event.sender)
        val change: Change =
            when (event.effectiveType) {
                "m.room.member" -> memberChange(event, content, members) ?: return null
                "m.room.name" -> Change.RoomName(content.str("name"))
                "m.room.topic" -> Change.RoomTopic(content.str("topic"))
                "m.room.avatar" -> Change.RoomAvatar
                "m.room.create" -> Change.RoomCreated
                "m.room.encryption" -> Change.EncryptionEnabled
                else -> return null
            }
        return TimelineItem.StateChange(
            key = "e:${event.rowId}",
            eventId = event.eventId,
            actor = event.sender,
            actorName = actor,
            actorAvatarMxc = members[event.sender]?.avatarMxc,
            change = change,
            timestamp = event.timestamp,
            reactions = reactionsOf(event, myReactions),
            readBy = readers[event.eventId].orEmpty().toReaders(me, event.sender, members),
        )
    }

    private fun memberChange(
        event: Event,
        content: JsonObject,
        members: Map<String, MemberProfile>,
    ): Change? {
        val target = event.stateKey ?: return null
        val targetName = content.str("displayname") ?: members[target]?.displayName ?: localpart(target)
        val previous = event.unsigned?.obj("prev_content")
        val was = previous?.str("membership")
        return when (content.str("membership")) {
            "join" -> {
                if (was != "join") Change.Joined else profileChange(previous, content)
            }

            "invite" -> {
                Change.Invited(targetName)
            }

            "leave" -> {
                if (event.sender == target) Change.Left else Change.Kicked(targetName, content.str("reason"))
            }

            "ban" -> {
                Change.Banned(targetName, content.str("reason"))
            }

            else -> {
                null
            }
        }
    }

    /** Member events loaded with the timeline are newer than any fetched snapshot: they win. */
    private fun membersFromTimeline(events: List<Event>): Map<String, MemberProfile> =
        events
            .filter {
                it.effectiveType == "m.room.member" && it.stateKey != null &&
                    it.effectiveContent.str("membership") == "join"
            }.associate {
                it.stateKey!! to
                    MemberProfile(it.effectiveContent.str("displayname"), it.effectiveContent.str("avatar_url"))
            }

    /** Our reactions, from the reaction events we happen to hold: target event → key → our event. */
    private fun myReactions(events: Collection<Event>): Map<String, Map<String, String>> =
        events
            .filter { it.sender == me && it.effectiveType == "m.reaction" && it.redactedBy == null }
            .mapNotNull { r ->
                val relates = r.effectiveContent.obj("m.relates_to") ?: return@mapNotNull null
                val target = relates.str("event_id") ?: return@mapNotNull null
                val key = relates.str("key") ?: return@mapNotNull null
                Triple(target, key, r.eventId)
            }.groupBy({ it.first }) { it.second to it.third }
            .mapValues { it.value.toMap() }

    private companion object {
        const val GROUP_GAP_MS = 5 * 60 * 1000L
        const val DAY_SEPARATOR_SLACK = 16
        const val PER_MESSAGE_PROFILE = "com.beeper.per_message_profile"

        /** gomuks' placeholder while it has no send result: not an error. */
        const val NOT_SENT = "not sent"
        const val PER_MESSAGE_PROFILE_STABLE = "m.per_message_profile"
        val MESSAGE_TYPES = setOf("m.room.message", "m.sticker", "m.room.encrypted")
        val STATE_TYPES =
            setOf("m.room.member", "m.room.name", "m.room.topic", "m.room.avatar", "m.room.create", "m.room.encryption")
        val HIDDEN_TYPES = setOf("m.reaction", "m.room.redaction")
    }
}
