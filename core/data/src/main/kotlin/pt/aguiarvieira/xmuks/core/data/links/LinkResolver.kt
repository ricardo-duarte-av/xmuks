package pt.aguiarvieira.xmuks.core.data.links

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.commands.MatrixLink
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult

/** Where a Matrix link leads. */
sealed interface LinkTarget {
    data class User(
        val userId: String,
    ) : LinkTarget

    /** A room we're in, and the event to show in it, if the link names one. */
    data class Room(
        val roomId: String,
        val eventId: String?,
    ) : LinkTarget

    /** A room we're not in: previewed, and joined or knocked on, through [via] servers. */
    data class NotJoined(
        val roomIdOrAlias: String,
        val via: List<String> = emptyList(),
    ) : LinkTarget

    /** Not a Matrix link, or an alias that doesn't resolve. */
    data object Unknown : LinkTarget
}

/**
 * Turns `matrix:` URIs (`u/`, `r/`, `roomid/`, with `/e/…` for an event) and matrix.to links into
 * something to open. Aliases are resolved through gomuks (`resolve_alias`).
 */
class LinkResolver(
    private val exec: ExecClient,
    private val rooms: RoomListRepository,
) {
    suspend fun resolve(uri: String): LinkTarget {
        val link = MatrixLink.parse(uri) ?: return LinkTarget.Unknown
        return when {
            link.id.startsWith("@") -> {
                LinkTarget.User(link.id)
            }

            link.id.startsWith("!") -> {
                room(link.id, link.eventId, link.via)
            }

            link.id.startsWith("#") -> {
                resolveAlias(link.id)?.let { room(it, link.eventId, link.via) }
                    ?: LinkTarget.NotJoined(link.id, link.via)
            }

            else -> {
                LinkTarget.Unknown
            }
        }
    }

    private suspend fun room(
        roomId: String,
        eventId: String?,
        via: List<String>,
    ): LinkTarget =
        if (rooms.room(roomId).first() != null) LinkTarget.Room(roomId, eventId) else LinkTarget.NotJoined(roomId, via)

    private suspend fun resolveAlias(alias: String): String? {
        val result = exec.exec("resolve_alias", buildJsonObject { put("alias", JsonPrimitive(alias)) }, ExecMode.Read)
        return ((result as? ExecResult.Ok)?.data as? JsonObject)?.str("room_id")
    }

    companion object {
        /** Whether [uri] is a Matrix link we'd open in the app. */
        fun isMatrixLink(uri: String) = MatrixLink.parse(uri) != null

        /** A link to [roomId] (and [eventId] in it), as notifications and shortcuts hand it to us. */
        fun roomUri(
            roomId: String,
            eventId: String? = null,
        ): String {
            fun enc(s: String) = java.net.URLEncoder.encode(s.removePrefix("!").removePrefix("$"), "UTF-8")
            return "matrix:roomid/${enc(roomId)}" + (eventId?.let { "/e/${enc(it)}" } ?: "")
        }
    }
}
