package pt.aguiarvieira.xmuks.core.data.roominfo

import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.protocol.Event

/** Why we can't post in a room: the composer gives way to a bar that says so. */
sealed interface SendBlock {
    /** The room was upgraded (`m.room.tombstone`): talk moved to [roomId]. */
    data class Replaced(
        val roomId: String,
        /** Where to join it through: the server of whoever replaced it, as the spec suggests. */
        val via: List<String>,
    ) : SendBlock

    /** The power levels don't let us send messages: [needed], and we have [mine]. */
    data class NoPermission(
        val needed: Long,
        val mine: Long,
    ) : SendBlock

    companion object {
        const val TOMBSTONE = "m.room.tombstone"

        /** What keeps [me] from posting in the room with [state]; null when nothing does. */
        fun of(
            roomId: String,
            state: List<Event>,
            me: String,
        ): SendBlock? = replaced(state) ?: of(RoomPermissions.of(roomId, state, me))

        /** What keeps us from posting messages, by [permissions]; null when nothing does. */
        fun of(permissions: RoomPermissions): SendBlock? = permissions.message?.let { NoPermission(it.needed, it.mine) }

        /** The room's tombstone, if it has one that names its successor. */
        fun replaced(state: List<Event>): Replaced? =
            state.firstOrNull { it.type == TOMBSTONE && it.stateKey == "" }?.let { replaced(it.content, it.sender) }

        /** A tombstone's [content], sent by [sender]: the room it points to, if it names one. */
        fun replaced(
            content: JsonObject,
            sender: String,
        ): Replaced? {
            val roomId = content.str("replacement_room")?.takeIf { it.startsWith("!") } ?: return null
            return Replaced(roomId, listOfNotNull(sender.substringAfter(':', "").ifEmpty { null }))
        }
    }
}
