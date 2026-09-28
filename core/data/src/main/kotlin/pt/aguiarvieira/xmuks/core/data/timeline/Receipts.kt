package pt.aguiarvieira.xmuks.core.data.timeline

import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.Receipt

/**
 * Who has read up to where: event ID → user IDs, newest reader first. Each user appears once, at
 * their newest main-timeline `m.read` receipt. A receipt on an event the timeline doesn't show
 * (a reaction, an edit, a redaction…) counts for the nearest shown event before it. Receipts move
 * live: [TimelineStore] keeps only each user's newest receipt, so a new one replaces the old.
 */
internal fun readersByEvent(
    snapshot: TimelineSnapshot,
    isShown: (Event) -> Boolean,
): Map<String, List<String>> {
    if (snapshot.receiptsByEventId.isEmpty()) return emptyMap()
    val placed = ArrayList<Pair<String, List<String>>>()
    var lastShown: String? = null
    for (event in snapshot.events) {
        if (isShown(event)) lastShown = event.eventId
        val users = snapshot.receiptsByEventId[event.eventId]?.filter(Receipt::isMainRead)?.map { it.userId }
        val target = lastShown
        if (!users.isNullOrEmpty() && target != null) placed += target to users
    }
    val out = LinkedHashMap<String, MutableList<String>>()
    val seen = HashSet<String>()
    for ((target, users) in placed.asReversed()) {
        users.forEach { if (seen.add(it)) out.getOrPut(target) { ArrayList() } += it }
    }
    return out
}

internal fun List<String>.toReaders(
    me: String?,
    sender: String,
    members: Map<String, MemberProfile>,
): List<Reader> =
    filter { it != me && it != sender }.map {
        Reader(it, members[it]?.displayName ?: localpart(it), members[it]?.avatarMxc)
    }

private fun Receipt.isMainRead() = receiptType == "m.read" && (threadId.isNullOrEmpty() || threadId == "main")
