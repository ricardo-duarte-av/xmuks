package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event

/** A poll (MSC3381): the question, its answers, how many each voter may pick, whether results show. */
data class Poll(
    val question: String,
    val answers: List<PollAnswer>,
    val maxSelections: Int,
    /** Results shown while it's open; otherwise only once it's ended. */
    val disclosed: Boolean,
    /** The stable (`m.poll.*`) events, not the unstable ones most clients still send. */
    val stable: Boolean = false,
)

data class PollAnswer(
    val id: String,
    val text: String,
)

/** Where a poll stands: votes per answer, ours, how many voted, whether it's ended. */
data class PollTally(
    val votes: Map<String, Int> = emptyMap(),
    val mine: Set<String> = emptySet(),
    val voters: Int = 0,
    val ended: Boolean = false,
)

/** The poll events, unstable and stable. */
object PollTypes {
    const val START = "org.matrix.msc3381.poll.start"
    const val RESPONSE = "org.matrix.msc3381.poll.response"
    const val END = "org.matrix.msc3381.poll.end"
    const val START_STABLE = "m.poll.start"
    const val RESPONSE_STABLE = "m.poll.response"
    const val END_STABLE = "m.poll.end"

    val starts = setOf(START, START_STABLE)
    val responses = setOf(RESPONSE, RESPONSE_STABLE)
    val ends = setOf(END, END_STABLE)
}

internal const val REFERENCE = "m.reference"
private const val TEXT = "org.matrix.msc1767.text"
private const val MESSAGE = "org.matrix.msc1767.message"
private const val STABLE_TEXT = "m.text"

/** The poll a start event's content describes; null when it doesn't describe one. */
internal fun pollOf(content: JsonObject): Poll? {
    val unstable = content.obj(PollTypes.START)
    val start = unstable ?: content.obj("m.poll") ?: return null
    val answers =
        (start["answers"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { a ->
                val id = a.str("id") ?: a.str("m.id") ?: return@mapNotNull null
                PollAnswer(id, textOf(a))
            }.distinctBy { it.id }
    if (answers.isEmpty()) return null
    val max = (start["max_selections"] as? JsonPrimitive)?.intOrNull ?: 1
    val kind = start.str("kind").orEmpty()
    return Poll(
        question = textOf(start["question"]),
        answers = answers,
        maxSelections = max.coerceIn(1, answers.size),
        disclosed = kind.endsWith("disclosed") && !kind.endsWith("undisclosed"),
        stable = unstable == null,
    )
}

/** MSC1767 text in any of its shapes: a plain string, a list of representations, or `body`. */
private fun textOf(element: JsonElement?): String {
    val obj = element as? JsonObject ?: return (element as? JsonPrimitive)?.contentOrNull.orEmpty()
    obj.str(TEXT)?.let { return it }
    val list = (obj[MESSAGE] ?: obj[STABLE_TEXT]) as? JsonArray
    val plain =
        list
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("mimetype") == null || it.str("mimetype") == "text/plain" }
            ?.str("body")
    return plain ?: obj.str("body").orEmpty()
}

/**
 * Counts [poll]'s votes from the [related] events we hold: each voter's latest response before
 * the poll ended (answers it doesn't have dropped, at most its max), the end only if the poll's
 * sender sent it.
 */
internal fun tally(
    pollId: String,
    pollSender: String,
    poll: Poll,
    related: Collection<Event>,
    me: String?,
): PollTally {
    val mine = related.filter { it.relatesTo == pollId && it.redactedBy == null }
    val end =
        mine
            .filter { it.effectiveType in PollTypes.ends && it.sender == pollSender }
            .minOfOrNull { it.timestamp }
    val valid = poll.answers.mapTo(HashSet()) { it.id }
    val latest =
        mine
            .filter { it.effectiveType in PollTypes.responses && (end == null || it.timestamp <= end) }
            .groupBy { it.sender }
            .mapValues { (_, responses) -> selections(responses.maxBy { it.timestamp }.effectiveContent) }
            .mapValues { (_, picks) -> picks.filter { it in valid }.distinct().take(poll.maxSelections) }
            .filterValues { it.isNotEmpty() }
    val votes =
        latest.values
            .flatten()
            .groupingBy { it }
            .eachCount()
    return PollTally(votes, latest[me].orEmpty().toSet(), latest.size, end != null)
}

private fun selections(content: JsonObject): List<String> {
    val picks = content.obj(PollTypes.RESPONSE)?.get("answers") ?: content["m.selections"]
    return (picks as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}

/** A new poll's content, as the unstable event gomuks and Element send, with a text fallback. */
internal fun pollStartContent(
    question: String,
    answers: List<PollAnswer>,
    maxSelections: Int,
    disclosed: Boolean,
): JsonObject {
    val fallback = question + answers.mapIndexed { i, a -> "\n${i + 1}. ${a.text}" }.joinToString("")
    return JsonObject(
        mapOf(
            PollTypes.START to
                JsonObject(
                    mapOf(
                        "question" to JsonObject(mapOf(TEXT to JsonPrimitive(question))),
                        "kind" to JsonPrimitive(if (disclosed) DISCLOSED else UNDISCLOSED),
                        "max_selections" to JsonPrimitive(maxSelections),
                        "answers" to JsonArray(answers.map(::answerJson)),
                    ),
                ),
            TEXT to JsonPrimitive(fallback),
            "body" to JsonPrimitive(fallback),
        ),
    )
}

private fun answerJson(answer: PollAnswer) =
    JsonObject(mapOf("id" to JsonPrimitive(answer.id), TEXT to JsonPrimitive(answer.text)))

private const val DISCLOSED = "org.matrix.msc3381.poll.disclosed"
private const val UNDISCLOSED = "org.matrix.msc3381.poll.undisclosed"

/** Our vote on [pollId]: [picks] replace whatever we chose before (none withdraws it). */
internal fun pollResponseContent(
    pollId: String,
    picks: List<String>,
    stable: Boolean,
): JsonObject {
    val answers = JsonArray(picks.map(::JsonPrimitive))
    return JsonObject(
        mapOf(
            "m.relates_to" to reference(pollId),
            if (stable) "m.selections" to answers else PollTypes.RESPONSE to JsonObject(mapOf("answers" to answers)),
        ),
    )
}

/** Ending [pollId]. */
internal fun pollEndContent(
    pollId: String,
    stable: Boolean,
): JsonObject =
    JsonObject(
        mapOf(
            "m.relates_to" to reference(pollId),
            (if (stable) "m.poll.end" else PollTypes.END) to JsonObject(emptyMap()),
            TEXT to JsonPrimitive("Ended poll"),
            "body" to JsonPrimitive("Ended poll"),
        ),
    )

private fun reference(eventId: String) =
    JsonObject(mapOf("rel_type" to JsonPrimitive(REFERENCE), "event_id" to JsonPrimitive(eventId)))
