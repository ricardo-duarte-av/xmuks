package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.obj
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.protocol.Event

enum class Membership {
    Join,
    Invite,
    Knock,
    Leave,
    Ban,
    ;

    companion object {
        fun of(value: String?): Membership =
            when (value) {
                "join" -> Join
                "invite" -> Invite
                "knock" -> Knock
                "ban" -> Ban
                else -> Leave
            }
    }
}

data class RoomMember(
    val userId: String,
    val displayName: String?,
    val avatarMxc: String?,
    val membership: Membership,
    val powerLevel: Long,
    /** Why they were kicked, banned, or asked to knock. */
    val reason: String?,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: userId
}

/** A room as its current state describes it: what the room info screen shows and edits. */
data class RoomInfo(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarMxc: String?,
    val canonicalAlias: String?,
    val altAliases: List<String>,
    val encrypted: Boolean,
    /** `public`, `invite`, `knock`, `restricted`, `knock_restricted` or `private`. */
    val joinRule: String,
    /** `world_readable`, `shared`, `invited` or `joined`. */
    val historyVisibility: String,
    val guestAccess: Boolean,
    val roomVersion: String,
    val isSpace: Boolean,
    /** The room this one was upgraded to. */
    val replacementRoom: String?,
    val powerLevels: PowerLevels,
    /** Everyone with a member event, ordered by level, then name. */
    val members: List<RoomMember>,
) {
    fun members(membership: Membership) = members.filter { it.membership == membership }

    fun member(userId: String) = members.firstOrNull { it.userId == userId }

    companion object {
        /** From `get_room_state`'s events (the current state, one per type and key). */
        fun parse(
            roomId: String,
            state: List<Event>,
        ): RoomInfo {
            val byKey = state.associateBy { it.type to it.stateKey.orEmpty() }

            fun content(
                type: String,
                key: String = "",
            ): JsonObject? = byKey[type to key]?.content

            val create = byKey["m.room.create" to ""]
            val version = create?.content?.str("room_version") ?: "1"
            val creators = creatorsOf(create, version)
            val levels = PowerLevels.parse(content(PowerLevels.TYPE), create?.sender, creators)
            val aliases = content("m.room.canonical_alias")
            val members =
                state
                    .filter { it.type == MEMBER && it.stateKey != null }
                    .map { member(it, levels) }
                    .sortedWith(
                        compareByDescending<RoomMember> { it.powerLevel }.thenBy {
                            it.name
                                .removePrefix(
                                    "@"
                                ).lowercase()
                        }
                    )
            return RoomInfo(
                roomId = roomId,
                name = content("m.room.name")?.str("name")?.takeIf { it.isNotBlank() },
                topic = content("m.room.topic")?.let(::topicOf),
                avatarMxc = content("m.room.avatar")?.str("url")?.takeIf { it.isNotBlank() },
                canonicalAlias = aliases?.str("alias")?.takeIf { it.isNotBlank() },
                altAliases = (aliases?.get("alt_aliases") as? JsonArray).orEmpty().mapNotNull { it.string() },
                encrypted = content("m.room.encryption")?.str("algorithm") != null,
                joinRule = content("m.room.join_rules")?.str("join_rule") ?: "invite",
                historyVisibility = content("m.room.history_visibility")?.str("history_visibility") ?: "shared",
                guestAccess = content("m.room.guest_access")?.str("guest_access") == "can_join",
                roomVersion = version,
                isSpace = create?.content?.str("type") == "m.space",
                replacementRoom = content("m.room.tombstone")?.str("replacement_room"),
                powerLevels = levels,
                members = members,
            )
        }

        private fun member(
            event: Event,
            levels: PowerLevels,
        ): RoomMember {
            val userId = event.stateKey.orEmpty()
            return RoomMember(
                userId = userId,
                displayName = event.content.str("displayname"),
                avatarMxc = event.content.str("avatar_url")?.takeIf { it.isNotBlank() },
                membership = Membership.of(event.content.str("membership")),
                powerLevel = levels.of(userId),
                reason = event.content.str("reason")?.takeIf { it.isNotBlank() },
            )
        }

        /** The plain topic, or MSC3765's `m.topic` text when that's all there is. */
        private fun topicOf(content: JsonObject): String? =
            content.str("topic")?.takeIf { it.isNotBlank() }
                ?: (content.obj("m.topic")?.get("m.text") as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.firstOrNull { it.str("mimetype") in listOf(null, "text/plain") }
                    ?.str("body")

        /** Room versions 12+ (MSC4289): the creator and `additional_creators` can't be outranked. */
        private fun creatorsOf(
            create: Event?,
            version: String,
        ): Set<String> {
            if (create == null || !privilegedCreators(version)) return emptySet()
            val additional = (create.content["additional_creators"] as? JsonArray).orEmpty().mapNotNull { it.string() }
            return (additional + create.sender).toSet()
        }

        private fun privilegedCreators(version: String) =
            version == "org.matrix.hydra.11" || (version.toIntOrNull() ?: 0) >= FIRST_PRIVILEGED_VERSION

        private fun kotlinx.serialization.json.JsonElement.string() = (this as? JsonPrimitive)?.contentOrNull

        private const val MEMBER = "m.room.member"
        private const val FIRST_PRIVILEGED_VERSION = 12
    }
}
