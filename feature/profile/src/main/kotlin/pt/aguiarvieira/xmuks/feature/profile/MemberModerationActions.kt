package pt.aguiarvieira.xmuks.feature.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.aguiarvieira.xmuks.core.data.roominfo.MemberStanding
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.ModerationRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.SentEvent

/**
 * Moderating [userId] in [roomId], from their profile: where they stand there, their role, kicking
 * and banning (and undoing them), and deleting what they sent recently.
 */
class MemberModerationActions(
    private val roomId: String,
    private val userId: String,
    private val roomInfo: RoomInfoRepository,
    private val moderation: ModerationRepository,
    private val tasks: ProfileTasks,
) {
    /** Null until loaded (or when it can't be: no card then). */
    private val _standing = MutableStateFlow<MemberStanding?>(null)
    val standing: StateFlow<MemberStanding?> = _standing.asStateFlow()

    /** Their recent events in the room, once looked for. */
    private val _recent = MutableStateFlow<List<SentEvent>?>(null)
    val recent: StateFlow<List<SentEvent>?> = _recent.asStateFlow()

    suspend fun load() {
        moderation.standing(roomId, userId).onSuccess { _standing.value = it }
    }

    /** Gives them [level] in the room, the rest of the power levels as they are. */
    fun setLevel(level: Long) =
        moderate { standing ->
            roomInfo.setState(roomId, PowerLevels.TYPE, standing.levels.withUser(userId, level))
        }

    fun setMembership(
        action: MembershipAction,
        reason: String?,
    ) = moderate { roomInfo.setMembership(roomId, userId, action, reason) }

    /** Bans them; with [deleteRecent], their recent events go too (and clients are asked to hide the rest). */
    fun ban(
        reason: String?,
        deleteRecent: Boolean,
        keepState: Boolean,
    ) = moderate {
        roomInfo
            .setMembership(roomId, userId, MembershipAction.Ban, reason, redactEvents = deleteRecent)
            .mapCatching { if (deleteRecent) redactRecent(keepState, reason) }
    }

    /** Looks for what they sent in the room's recent history, to say how much deleting would remove. */
    fun findRecent() {
        _recent.value = null
        tasks.run({ moderation.recentEvents(roomId, userId).onSuccess { _recent.value = it } })
    }

    fun deleteRecent(
        keepState: Boolean,
        reason: String?,
    ) = moderate { runCatching { redactRecent(keepState, reason) } }

    private suspend fun redactRecent(
        keepState: Boolean,
        reason: String?,
    ) {
        val events = _recent.value ?: moderation.recentEvents(roomId, userId).getOrThrow()
        moderation.redact(roomId, events.filter { !keepState || !it.state }.map { it.eventId }, reason)
        _recent.value = null
    }

    /** Runs one change against where they stand now, then reloads it. */
    private fun moderate(block: suspend (MemberStanding) -> Result<Unit>) {
        val standing = _standing.value ?: return
        tasks.run({ block(standing) }, then = ::load)
    }
}
