package pt.aguiarvieira.xmuks.core.data.timeline

import pt.aguiarvieira.xmuks.core.protocol.Event

/** One reaction under a message, and everyone who reacted with it, earliest first. */
data class ReactionGroup(
    val key: String,
    /** A custom emoji's shortcode (without colons), as its first sender gave it. */
    val shortcode: String?,
    val reactors: List<Reactor>,
) {
    /** Custom emoji (image) reactions use an mxc URI as their key. */
    val isImage: Boolean get() = key.startsWith("mxc://")
}

data class Reactor(
    val userId: String,
    val name: String,
    val avatarMxc: String?,
    val timestamp: Long,
)

/**
 * [events] (a message's m.annotation relations) grouped by reaction: the most reacted first,
 * ties by who got there first. Deleted reactions and repeats of the same one by the same person
 * don't count.
 */
internal fun reactionGroups(
    events: List<Event>,
    members: Map<String, MemberProfile>,
): List<ReactionGroup> =
    events
        .asSequence()
        .filter { it.effectiveType == "m.reaction" && it.redactedBy == null }
        .mapNotNull { event ->
            event.effectiveContent
                .obj("m.relates_to")
                ?.str("key")
                ?.let { it to event }
        }.groupBy({ it.first }, { it.second })
        .map { (key, reactions) ->
            val earliest = reactions.sortedBy { it.timestamp }.distinctBy { it.sender }
            ReactionGroup(
                key = key,
                shortcode = earliest.firstNotNullOfOrNull { shortcodeOf(it) },
                reactors =
                    earliest.map { event ->
                        val profile = members[event.sender]
                        Reactor(
                            userId = event.sender,
                            name = profile?.displayName ?: localpart(event.sender),
                            avatarMxc = profile?.avatarMxc,
                            timestamp = event.timestamp,
                        )
                    },
            )
        }.sortedWith(compareByDescending<ReactionGroup> { it.reactors.size }.thenBy { it.reactors.first().timestamp })

private fun shortcodeOf(event: Event): String? =
    event.effectiveContent
        .str("com.beeper.reaction.shortcode")
        ?.trim(':')
        ?.takeIf { it.isNotBlank() }
