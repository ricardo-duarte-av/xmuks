package pt.aguiarvieira.xmuks.feature.room

import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/**
 * Where what's sent goes: a reply to the message chosen, or — in a thread — into the thread,
 * answering the message chosen there or else (for clients without threads) its latest.
 */
internal class SendTarget(
    private val modes: ComposeModes,
    /** The thread being shown; null in the room's main timeline. */
    private val threadRoot: String?,
    /** What's shown, newest first. */
    private val items: () -> List<TimelineItem>?,
) {
    fun reply(): ReplyTarget? {
        val chosen = (modes.current.value as? ComposeMode.Reply)?.message
        if (threadRoot == null) return chosen?.let { ReplyTarget(it.eventId, it.sender) }
        if (chosen != null) return ReplyTarget(chosen.eventId, chosen.sender, threadRoot)
        val latest =
            items()
                ?.filterIsInstance<TimelineItem.Message>()
                ?.firstOrNull { it.eventId.startsWith("$") }
        return ReplyTarget(latest?.eventId ?: threadRoot, latest?.sender.orEmpty(), threadRoot, fallback = true)
    }
}
