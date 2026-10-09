package pt.aguiarvieira.xmuks.core.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/** An in-call reaction Element Call knows (its `name` picks the sound and effect it plays). */
data class CallReaction(
    val emoji: String,
    val name: String,
)

/**
 * Raised hands and in-call reactions, the way Element Call sends them, so both sides see each
 * other's: a hand is an `m.reaction` 🖐️ on the member's call membership event (lowered by redacting
 * it); a reaction is an `io.element.call.reaction` referencing it, shown for a few seconds. A hand
 * belongs to one membership event: when the member's membership is re-sent, it's gone.
 *
 * Participants are keyed `user:device`.
 */
class CallReactions(
    private val api: RtcApi,
    private val roomId: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Hand(
        val reactionEventId: String,
        val membershipEventId: String,
        val raisedAt: Long,
    )

    private val mutableHands = MutableStateFlow<Map<String, Hand>>(emptyMap())
    val hands: StateFlow<Map<String, Hand>> = mutableHands.asStateFlow()

    private val mutableReactions = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The emoji someone just sent, by participant, while it shows. */
    val reactions: StateFlow<Map<String, String>> = mutableReactions.asStateFlow()

    /**
     * A room event; [memberOf] finds the participant whose current membership event has that id.
     */
    fun onEvent(
        event: Event,
        memberOf: (membershipEventId: String) -> String?,
    ) {
        when (event.effectiveType) {
            REACTION -> {
                onReaction(event, memberOf)
            }

            REDACTION -> {
                event.effectiveContent.string("redacts")?.let(::onRedacted)
            }

            RtcTypes.CALL_REACTION -> {
                val target = relation(event, REFERENCE) ?: return
                val key = memberOf(target) ?: return
                val emoji = event.effectiveContent.string("emoji") ?: return
                show(key, emoji.take(MAX_EMOJI_CHARS))
            }
        }
    }

    private fun onReaction(
        event: Event,
        memberOf: (String) -> String?,
    ) {
        val relates = event.effectiveContent["m.relates_to"] as? JsonObject ?: return
        if (relates.string("rel_type") != ANNOTATION || relates.string("key") != HAND) return
        val target = relates.string("event_id") ?: return
        val key = memberOf(target) ?: return
        if (!key.startsWith(event.sender + ":")) return
        if (event.redactedBy != null) {
            onRedacted(event.eventId)
        } else {
            mutableHands.update { it + (key to Hand(event.eventId, target, event.timestamp)) }
        }
    }

    private fun onRedacted(eventId: String) {
        mutableHands.update { hands -> hands.filterValues { it.reactionEventId != eventId } }
    }

    /** The call's members now: hands of those gone, or whose membership changed, come down. */
    fun membersChanged(currentMembershipEvents: Map<String, String>) {
        mutableHands.update { hands ->
            hands.filter { (key, hand) ->
                currentMembershipEvents[key] ==
                    hand.membershipEventId
            }
        }
    }

    suspend fun raiseHand(
        ownKey: String,
        membershipEventId: String,
    ) {
        if (ownKey in mutableHands.value) return
        val content =
            buildJsonObject {
                put(
                    "m.relates_to",
                    buildJsonObject {
                        put("rel_type", ANNOTATION)
                        put("event_id", membershipEventId)
                        put("key", HAND)
                    },
                )
            }
        api.sendEvent(roomId, REACTION, content).onSuccess { id ->
            mutableHands.update { it + (ownKey to Hand(id, membershipEventId, clock())) }
        }
    }

    suspend fun lowerHand(ownKey: String) {
        val hand = mutableHands.value[ownKey] ?: return
        mutableHands.update { it - ownKey }
        api.redact(roomId, hand.reactionEventId)
    }

    /** Sends [reaction]; one at a time, as Element Call allows. */
    suspend fun react(
        ownKey: String,
        membershipEventId: String,
        reaction: CallReaction,
    ) {
        if (ownKey in mutableReactions.value) return
        show(ownKey, reaction.emoji)
        val content =
            buildJsonObject {
                put(
                    "m.relates_to",
                    buildJsonObject {
                        put("rel_type", REFERENCE)
                        put("event_id", membershipEventId)
                    },
                )
                put("emoji", reaction.emoji)
                put("name", reaction.name)
            }
        api.sendEvent(roomId, RtcTypes.CALL_REACTION, content)
    }

    private fun show(
        key: String,
        emoji: String,
    ) {
        mutableReactions.update { it + (key to emoji) }
        scope.launch {
            delay(SHOW_MS)
            mutableReactions.update { if (it[key] == emoji) it - key else it }
        }
    }

    private fun relation(
        event: Event,
        type: String,
    ): String? {
        val relates = event.effectiveContent["m.relates_to"] as? JsonObject ?: return null
        return relates.string("event_id")?.takeIf { relates.string("rel_type") == type }
    }

    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val HAND = "🖐️"
        private const val REACTION = "m.reaction"
        private const val REDACTION = "m.room.redaction"
        private const val ANNOTATION = "m.annotation"
        private const val REFERENCE = "m.reference"
        private const val SHOW_MS = 3_000L
        private const val MAX_EMOJI_CHARS = 16

        /** Element Call's reactions, in its order, with its names. */
        val SET =
            listOf(
                CallReaction("👍", "thumbsup"),
                CallReaction("🎉", "party"),
                CallReaction("👏", "clapping"),
                CallReaction("🐶", "dog"),
                CallReaction("🐱", "cat"),
                CallReaction("💡", "lightbulb"),
                CallReaction("🦗", "crickets"),
                CallReaction("👎", "thumbsdown"),
                CallReaction("😵‍💫", "dizzy"),
                CallReaction("👌", "ok"),
                CallReaction("🥰", "heart"),
                CallReaction("😄", "laugh"),
                CallReaction("🦌", "deer"),
                CallReaction("🤘", "rock"),
                CallReaction("👋", "wave"),
                CallReaction("🥁", "drum"),
                CallReaction("🛎️", "bell"),
            )
    }
}
