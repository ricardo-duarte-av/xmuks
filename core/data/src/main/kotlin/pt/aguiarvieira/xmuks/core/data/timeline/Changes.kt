package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.protocol.Event

/** What a replied-to event without text is: the quote says so instead of showing nothing. */
internal fun replyKind(
    event: Event,
    members: Map<String, MemberProfile>,
): ReplyKind {
    if (event.redactedBy != null) return ReplyKind.Deleted
    if (event.type == "m.room.encrypted" && event.decrypted == null) return ReplyKind.Undecryptable
    val content = event.effectiveContent
    return when (val type = event.effectiveType) {
        "m.reaction" -> ReplyKind.Reaction(content.obj("m.relates_to")?.str("key"))
        "m.room.pinned_events" -> ReplyKind.PinsChanged
        "m.room.power_levels" -> ReplyKind.PermissionsChanged
        else -> roomChange(event, content, members)?.let(ReplyKind::Changed) ?: ReplyKind.Other(type)
    }
}

/** A membership or room change, as the timeline shows it; null for anything else. */
internal fun roomChange(
    event: Event,
    content: JsonObject,
    members: Map<String, MemberProfile>,
): Change? =
    when (event.effectiveType) {
        "m.room.member" -> memberChange(event, content, members)
        "m.room.name" -> Change.RoomName(content.str("name"))
        "m.room.topic" -> Change.RoomTopic(content.str("topic"))
        "m.room.avatar" -> Change.RoomAvatar
        "m.room.create" -> Change.RoomCreated
        "m.room.encryption" -> Change.EncryptionEnabled
        else -> null
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
