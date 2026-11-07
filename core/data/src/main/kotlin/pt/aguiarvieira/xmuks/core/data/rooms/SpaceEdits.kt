package pt.aguiarvieira.xmuks.core.data.rooms

import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.database.RoomEntity
import pt.aguiarvieira.xmuks.core.database.SpaceEdgeEntity

/** A room the space editor offers: any room we're in, spaces and DMs included. */
data class SpaceEditCandidate(
    val roomId: String,
    val name: String,
    val avatarUrl: String?,
    val isSpace: Boolean,
    val isDirect: Boolean,
    /** The other spaces it's directly in, by name: a hint for rooms nested further down. */
    val parents: List<String>,
)

/** What the editor needs about one space: its children now, and every room it could have. */
data class SpaceEditData(
    /** Direct children we're in (rooms we're not in can't be listed, so they're never touched). */
    val children: Set<String>,
    /** Most recently active first. */
    val candidates: List<SpaceEditCandidate>,
)

/** The pure part of editing a space's children: what to offer, what to send. */
object SpaceEdits {
    /** Every space [spaceId] is below, at any depth (cycle-safe). */
    fun ancestors(
        spaceId: String,
        edges: List<SpaceEdgeEntity>,
    ): Set<String> {
        val parentsOf = edges.groupBy({ it.childId }, { it.spaceId })
        val found = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(spaceId))
        while (queue.isNotEmpty()) {
            parentsOf[queue.removeFirst()].orEmpty().forEach { if (found.add(it)) queue.add(it) }
        }
        return found - spaceId
    }

    /**
     * [rooms] (most recent first) as the editor lists them for [spaceId]: neither the space itself
     * nor anything above it (that would make a loop), nor upgraded-away rooms unless they're
     * already in it (so they can still be taken out).
     */
    fun data(
        spaceId: String,
        rooms: List<RoomEntity>,
        edges: List<SpaceEdgeEntity>,
        avatar: (String?) -> String? = { it },
    ): SpaceEditData {
        val excluded = ancestors(spaceId, edges) + spaceId
        val direct = edges.filter { it.spaceId == spaceId }.mapTo(HashSet()) { it.childId }
        val names = rooms.associate { it.roomId to (it.name ?: it.roomId) }
        val parents =
            edges
                .filter { it.spaceId != spaceId && it.spaceId in names }
                .groupBy({ it.childId }, { names.getValue(it.spaceId) })
        val candidates =
            rooms
                .filter { it.roomId !in excluded && (it.replacementRoom == null || it.roomId in direct) }
                .map {
                    SpaceEditCandidate(
                        roomId = it.roomId,
                        name = it.name ?: it.roomId,
                        avatarUrl = avatar(it.avatar),
                        isSpace = it.isSpace,
                        isDirect = it.dmUserId != null,
                        parents = parents[it.roomId].orEmpty().sorted(),
                    )
                }
        val listed = candidates.mapTo(HashSet()) { it.roomId }
        return SpaceEditData(direct intersect listed, candidates)
    }

    /**
     * Servers a child can be joined through (`via`), as gomuks web picks them: our own, then the
     * server of the highest-ranked member above the default level, then the busiest servers.
     */
    fun viaServers(
        ownUserId: String,
        room: RoomInfo,
        max: Int = MAX_VIA,
    ): List<String> {
        val joined = room.members(Membership.Join)
        val levels = room.powerLevels
        val powerful =
            joined
                .filter { it.powerLevel > levels.usersDefault }
                .maxByOrNull { it.powerLevel }
                ?.let { serverOf(it.userId) }
        val busiest =
            joined
                .groupingBy { serverOf(it.userId) }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
        return (listOf(serverOf(ownUserId), powerful) + busiest).filterNotNull().distinct().take(max)
    }

    /** The server part of a Matrix ID: everything after the first colon. */
    fun serverOf(userId: String): String? = userId.substringAfter(':', "").takeIf { it.isNotEmpty() }

    const val MAX_VIA = 3
}
