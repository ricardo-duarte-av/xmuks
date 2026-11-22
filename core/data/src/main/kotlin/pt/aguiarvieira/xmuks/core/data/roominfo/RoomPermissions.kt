package pt.aguiarvieira.xmuks.core.data.roominfo

import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.Pins
import pt.aguiarvieira.xmuks.core.data.timeline.PollTypes
import pt.aguiarvieira.xmuks.core.protocol.Event

/**
 * What our power level lets us do in a room, action by action: a [Limit.NoPermission] (the level
 * it needs, and ours) where it doesn't, null where it does. Gated actions stay offered, dimmed,
 * and say why. What's checked is the type that actually goes out: in an encrypted room that's
 * `m.room.encrypted`, but for reactions (gomuks sends them in the clear) and redactions (never
 * encrypted).
 */
data class RoomPermissions(
    private val levels: PowerLevels?,
    private val me: String,
    private val encrypted: Boolean,
) {
    /** Sending a (non-state) event of [type]. */
    fun send(type: String): Limit.NoPermission? {
        val levels = levels ?: return null
        val outer = if (encrypted && type !in CLEAR) ENCRYPTED else type
        return need(levels.forEvent(outer))
    }

    /** Setting state of [type]. */
    fun setState(type: String): Limit.NoPermission? = levels?.let { need(it.forState(type)) }

    /** Messages of any kind: text, media, voice, locations, replies, edits, thread posts. */
    val message: Limit.NoPermission? get() = send(MESSAGE)

    val sticker: Limit.NoPermission? get() = send(STICKER)

    val reaction: Limit.NoPermission? get() = send(REACTION)

    /** Starting a poll (we send the unstable type, as most clients still do). */
    val pollStart: Limit.NoPermission? get() = send(PollTypes.START)

    /** Voting in a poll, in its own flavour of events. */
    fun pollVote(stable: Boolean): Limit.NoPermission? =
        send(if (stable) PollTypes.RESPONSE_STABLE else PollTypes.RESPONSE)

    fun pollEnd(stable: Boolean): Limit.NoPermission? = send(if (stable) PollTypes.END_STABLE else PollTypes.END)

    /** Deleting our own events (and taking back our reactions): only the redaction event's level. */
    val deleteOwn: Limit.NoPermission? get() = send(PowerLevels.REDACTION)

    /** Deleting others' events: the redact level too. */
    val deleteOthers: Limit.NoPermission? get() = levels?.let { need(it.redactOthers) }

    val pin: Limit.NoPermission? get() = setState(Pins.TYPE)

    private fun need(needed: Long): Limit.NoPermission? {
        val mine = levels?.of(me) ?: return null
        return if (mine < needed) Limit.NoPermission(needed, mine) else null
    }

    companion object {
        const val MESSAGE = "m.room.message"
        const val STICKER = "m.sticker"
        const val REACTION = "m.reaction"
        private const val ENCRYPTED = "m.room.encrypted"

        /** Sent unencrypted even in encrypted rooms. */
        private val CLEAR = setOf(REACTION, PowerLevels.REDACTION)

        /** Before the room's state is in: nothing held back (the server has the last word). */
        val Unknown = RoomPermissions(null, "", false)

        /** [me]'s, in the room with [state]. */
        fun of(
            roomId: String,
            state: List<Event>,
            me: String?,
        ): RoomPermissions {
            if (me == null || state.isEmpty()) return Unknown
            val info = RoomInfo.parse(roomId, state)
            return RoomPermissions(info.powerLevels, me, info.encrypted)
        }
    }
}
