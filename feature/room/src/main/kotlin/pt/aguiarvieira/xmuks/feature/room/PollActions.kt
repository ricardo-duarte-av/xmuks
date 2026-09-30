package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.RoomPolls
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** A poll being written: the question, its answers, and its rules. */
data class PollDraft(
    val question: String,
    val answers: List<String>,
    val maxSelections: Int = 1,
    /** Results visible while it's open. */
    val disclosed: Boolean = true,
)

/** Starting, answering and ending polls. */
class PollActions(
    val onStart: (PollDraft) -> Unit = {},
    /** Our answers to a poll become these (none withdraws our vote). */
    val onVote: (TimelineItem.Message, List<String>) -> Unit = { _, _ -> },
    val onEnd: (TimelineItem.Message) -> Unit = {},
)

/** [PollActions] done through a room's [RoomPolls]. */
internal fun pollActions(
    scope: CoroutineScope,
    polls: RoomPolls,
) = PollActions(
    onStart = { d -> scope.launch { polls.start(d.question, d.answers, d.maxSelections, d.disclosed) } },
    onVote = { message, picks ->
        val poll = (message.content as? MessageContent.Poll)?.poll
        if (poll != null) scope.launch { polls.vote(message.eventId, poll, picks) }
    },
    onEnd = { message ->
        val poll = (message.content as? MessageContent.Poll)?.poll
        if (poll != null) scope.launch { polls.end(message.eventId, poll) }
    },
)

/** What tapping [answer] makes our picks: one-choice polls switch, others toggle up to their max. */
internal fun picksAfter(
    content: MessageContent.Poll,
    answer: String,
): List<String> {
    val mine = content.tally.mine
    val max = content.poll.maxSelections
    return when {
        max == 1 -> {
            listOf(answer)
        }

        answer in mine -> {
            content.poll.answers
                .map { it.id }
                .filter { it in mine && it != answer }
        }

        else -> {
            (
                content.poll.answers
                    .map { it.id }
                    .filter { it in mine } + answer
            ).takeLast(max)
        }
    }
}
