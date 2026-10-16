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

    /** The level needed to send (non-state) events of [type]. */
    fun forEvent(type: String): Long = events[type] ?: eventsDefault

    fun canSend(
        userId: String,
        type: String,
    ) = of(userId) >= forEvent(type)

    /**
     * Whether [userId] may be in a call here: a call membership is either state (legacy) or a
     * sticky event (Matrix 2.0), and either will do.
     */
    fun canCall(userId: String) = canSetState(userId, CALL_MEMBER_STATE) || canSend(userId, CALL_MEMBER_STICKY)

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

    /** The level needed for a whole kind of action. */
    fun of(threshold: Threshold): Long =
        when (threshold) {
            Threshold.UsersDefault -> usersDefault
            Threshold.EventsDefault -> eventsDefault
            Threshold.StateDefault -> stateDefault
            Threshold.Invite -> invite
            Threshold.Kick -> kick
            Threshold.Ban -> ban
            Threshold.Redact -> redact
            Threshold.RoomNotification -> raw.obj(NOTIFICATIONS)?.level(ROOM) ?: DEFAULT_MODERATION
        }

    /**
     * Whether [actor] may move a level (a threshold or an event type's) from [current] to [newLevel]:
     * they need the right to change power levels, and both must be within their own.
     */
    fun canChange(
        actor: String,
        current: Long,
        newLevel: Long? = null,
    ): Boolean {
        val mine = of(actor)
        return canSetState(actor, TYPE) && current <= mine && (newLevel == null || newLevel <= mine)
    }

    /** The content with [threshold] at [level]. */
    fun with(
        threshold: Threshold,
        level: Long,
    ): JsonObject {
        val key = threshold.key
        if (threshold != Threshold.RoomNotification) return JsonObject(base + (key to JsonPrimitive(level)))
        val notifications = raw.obj(NOTIFICATIONS) ?: JsonObject(emptyMap())
        return JsonObject(base + (NOTIFICATIONS to JsonObject(notifications + (ROOM to JsonPrimitive(level)))))
    }

    /** The content with events of [type] needing [level], or (null) back to the default for their kind. */
    fun withEvent(
        type: String,
        level: Long?,
    ): JsonObject {
        val updated = events.toMutableMap()
        if (level == null) updated.remove(type) else updated[type] = level
        return JsonObject(base + (EVENTS to buildJsonObject { updated.forEach { (t, l) -> put(t, JsonPrimitive(l)) } }))
    }

    /** The content with [userId] at [level] (back to the default removes the entry). */
    fun withUser(
        userId: String,
        level: Long,
    ): JsonObject {
        val updated = users.toMutableMap()
        if (level == usersDefault) updated.remove(userId) else updated[userId] = level
        return JsonObject(
            base + (USERS to buildJsonObject { updated.forEach { (id, l) -> put(id, JsonPrimitive(l)) } }),
        )
    }

    /**
     * What a change starts from: the event's content, or, in a room without one, the levels in
     * effect spelled out (sending only the changed key would put everything else at the spec's
     * defaults for an existing event, taking the creator's 100 away).
     */
    private val base: JsonObject
        get() =
            raw.ifEmpty {
                fun levels(map: Map<String, Long>) =
                    buildJsonObject { map.forEach { (k, l) -> put(k, JsonPrimitive(l)) } }
                buildJsonObject {
                    put(USERS, levels(users))
                    put("users_default", JsonPrimitive(usersDefault))
                    put(EVENTS, levels(events))
                    put("events_default", JsonPrimitive(eventsDefault))
                    put("state_default", JsonPrimitive(stateDefault))
                    put("ban", JsonPrimitive(ban))
                    put("kick", JsonPrimitive(kick))
                    put("invite", JsonPrimitive(invite))
                    put("redact", JsonPrimitive(redact))
                }
            }

    companion object {
        const val TYPE = "m.room.power_levels"
        const val CALL_MEMBER_STATE = "org.matrix.msc3401.call.member"
        const val CALL_MEMBER_STICKY = "org.matrix.msc4143.rtc.member"
        const val CREATOR = Long.MAX_VALUE
        const val ADMIN = 100L
        const val MODERATOR = 50L
        private const val USERS = "users"
        private const val EVENTS = "events"
        private const val NOTIFICATIONS = "notifications"
        private const val ROOM = "room"
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
                events = content.levels(EVENTS),
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

/** The levels for whole kinds of actions, by where they are in the content. */
enum class Threshold(
    val key: String,
) {
    UsersDefault("users_default"),
    EventsDefault("events_default"),
    StateDefault("state_default"),
    Invite("invite"),
    Kick("kick"),
    Ban("ban"),
    Redact("redact"),

    /** Under `notifications`: who may ping everyone with @room. */
    RoomNotification("room"),
}
