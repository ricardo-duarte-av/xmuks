package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.obj

/**
 * `m.room.power_levels`: who may do what. [creators] (room version 12 and later) outrank everyone
 * and can't be demoted; in older rooms they're ordinary users with whatever level they were given.
 */
data class PowerLevels(
    val users: Map<String, Long>,
    val usersDefault: Long,
    val events: Map<String, Long>,
    val eventsDefault: Long,
    val stateDefault: Long,
    val ban: Long,
    val kick: Long,
    val invite: Long,
    val redact: Long,
    val creators: Set<String> = emptySet(),
    /** The event's content as it is, so a change keeps every field we don't model. */
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    fun of(userId: String): Long = if (userId in creators) CREATOR else users[userId] ?: usersDefault

    /** The level needed to send state of [type]. */
    fun forState(type: String): Long = events[type] ?: stateDefault

    fun canSetState(
        userId: String,
        type: String,
    ) = of(userId) >= forState(type)

    fun canInvite(userId: String) = of(userId) >= invite

    fun canKick(
        actor: String,
        target: String,
    ) = actor != target && of(actor) >= kick && of(actor) > of(target)

    fun canBan(
        actor: String,
        target: String,
    ) = actor != target && of(actor) >= ban && of(actor) > of(target)

    /**
     * Whether [actor] may give [target] a new level: they need the right to change power levels, to
     * outrank the target (or be the target, demoting themselves), and can't give more than they have.
     */
    fun canChangeLevel(
        actor: String,
        target: String,
        newLevel: Long? = null,
    ): Boolean {
        if (target in creators || !canSetState(actor, TYPE)) return false
        val mine = of(actor)
        val outranks = actor == target || mine > of(target)
        return outranks && (newLevel == null || newLevel <= mine)
    }

    /** The content with [userId] at [level] (back to the default removes the entry). */
    fun withUser(
        userId: String,
        level: Long,
    ): JsonObject {
        val updated = users.toMutableMap()
        if (level == usersDefault) updated.remove(userId) else updated[userId] = level
        return JsonObject(
            raw + (USERS to buildJsonObject { updated.forEach { (id, l) -> put(id, JsonPrimitive(l)) } }),
        )
    }

    companion object {
        const val TYPE = "m.room.power_levels"
        const val CREATOR = Long.MAX_VALUE
        const val ADMIN = 100L
        const val MODERATOR = 50L
        private const val USERS = "users"
        private const val DEFAULT_MODERATION = 50L

        /**
         * Parses [content]; without a power levels event, the spec's defaults: the creator at 100,
         * everyone else at 0, and state open to all.
         */
        fun parse(
            content: JsonObject?,
            creator: String?,
            creators: Set<String>,
        ): PowerLevels {
            if (content == null) {
                return PowerLevels(
                    users = creator?.let { mapOf(it to ADMIN) }.orEmpty(),
                    usersDefault = 0,
                    events = emptyMap(),
                    eventsDefault = 0,
                    stateDefault = 0,
                    ban = DEFAULT_MODERATION,
                    kick = DEFAULT_MODERATION,
                    invite = 0,
                    redact = DEFAULT_MODERATION,
                    creators = creators,
                )
            }
            return PowerLevels(
                users = content.levels(USERS),
                usersDefault = content.level("users_default") ?: 0,
                events = content.levels("events"),
                eventsDefault = content.level("events_default") ?: 0,
                stateDefault = content.level("state_default") ?: DEFAULT_MODERATION,
                ban = content.level("ban") ?: DEFAULT_MODERATION,
                kick = content.level("kick") ?: DEFAULT_MODERATION,
                invite = content.level("invite") ?: 0,
                redact = content.level("redact") ?: DEFAULT_MODERATION,
                creators = creators,
                raw = content,
            )
        }

        /** Levels are integers, though some servers let strings through (old room versions). */
        private fun JsonObject.level(key: String): Long? =
            (get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }

        private fun JsonObject.levels(key: String): Map<String, Long> =
            obj(key)
                ?.keys
                ?.mapNotNull { k -> obj(key)?.level(k)?.let { k to it } }
                ?.toMap()
                .orEmpty()
    }
}
