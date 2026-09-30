package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.util.UUID

/**
 * Polls in one room: their votes (those in the timeline, plus every other one gomuks holds,
 * fetched once per poll), and starting, answering and ending them.
 */
class RoomPolls(
    private val roomId: String,
    private val exec: ExecClient,
    private val outbox: Outbox,
    private val scope: CoroutineScope,
) {
    /** Votes and ends gomuks holds, by poll: those that happened before the timeline we have. */
    private val fetched = MutableStateFlow<Map<String, List<Event>>>(emptyMap())
    private val asked = HashSet<String>()

    /** [snapshots] with every vote on their polls among the events held. */
    fun withVotes(snapshots: Flow<TimelineSnapshot>): Flow<TimelineSnapshot> =
        combine(snapshots, fetched) { snap, votes ->
            fetchMissing(snap)
            if (votes.isEmpty()) {
                snap
            } else {
                snap.copy(eventsByRowId = snap.eventsByRowId + votes.values.flatten().associateBy { it.rowId })
            }
        }

    private fun fetchMissing(snap: TimelineSnapshot) {
        val polls =
            snap.events
                .filter { it.effectiveType in PollTypes.starts && it.eventId.startsWith("$") }
                .map { it.eventId }
        val missing = synchronized(asked) { polls.filter(asked::add) }
        for (pollId in missing) {
            scope.launch {
                val related = related(pollId) ?: return@launch
                fetched.update { it + (pollId to related) }
            }
        }
    }

    private suspend fun related(pollId: String): List<Event>? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(pollId))
                put("relation_type", JsonPrimitive(REFERENCE))
            }
        val result = exec.exec("get_related_events", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        return runCatching {
            GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), result.data)
        }.getOrNull()
    }

    suspend fun start(
        question: String,
        answers: List<String>,
        maxSelections: Int,
        disclosed: Boolean,
    ) {
        val options = answers.map { PollAnswer(UUID.randomUUID().toString().take(ID_LENGTH), it) }
        send(PollTypes.START, pollStartContent(question, options, maxSelections.coerceIn(1, options.size), disclosed))
    }

    /** Our answer to [pollId] becomes [picks]; none withdraws it. */
    suspend fun vote(
        pollId: String,
        poll: Poll,
        picks: List<String>,
    ) = send(
        if (poll.stable) PollTypes.RESPONSE_STABLE else PollTypes.RESPONSE,
        pollResponseContent(pollId, picks, poll.stable),
    )

    suspend fun end(
        pollId: String,
        poll: Poll,
    ) = send(if (poll.stable) PollTypes.END_STABLE else PollTypes.END, pollEndContent(pollId, poll.stable))

    private suspend fun send(
        type: String,
        content: JsonObject,
    ) {
        outbox.enqueue(
            roomId,
            "send_event",
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(type))
                put("content", content)
            },
        )
    }

    private companion object {
        const val ID_LENGTH = 8
    }
}
