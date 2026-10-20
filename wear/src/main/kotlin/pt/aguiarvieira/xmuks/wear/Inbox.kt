package pt.aguiarvieira.xmuks.wear

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.notify.Notified
import pt.aguiarvieira.xmuks.core.notify.PushMessage
import pt.aguiarvieira.xmuks.core.notify.PushUser
import pt.aguiarvieira.xmuks.core.notify.notifiedOf
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Past notifications, newest first, from gomuks' own record of them (`get_mentions`). The watch
 * keeps no rooms, so each page's room names and avatars and its senders' profiles come in one
 * `get_specific_room_state`. A room without a name (or alias) is taken for a DM: its sender's
 * name and avatar stand for the room's, as they do in the DM's notification.
 */
@Singleton
class Inbox
    @Inject
    constructor(
        private val exec: ExecClient,
    ) {
        /** Which notifications: those that highlighted (mentions, keywords), or everything that notified. */
        enum class Kind(
            /** gomuks' unread type bits. */
            val unreadType: Int,
        ) {
            Mentions(HIGHLIGHT),
            All(NOTIFY),
        }

        /** Every message loaded so far, by event ID: what the message screen opens. */
        private val loaded = ConcurrentHashMap<String, Notified>()

        fun find(eventId: String): Notified? = loaded[eventId]

        /** Up to [limit] notifications older than [before] (a timestamp). */
        suspend fun page(
            kind: Kind,
            before: Long,
            limit: Int = PAGE,
        ): Result<List<Notified>> {
            val params =
                buildJsonObject {
                    put("max_timestamp", JsonPrimitive(before))
                    put("type", JsonPrimitive(kind.unreadType))
                    put("limit", JsonPrimitive(limit))
                }
            val events = read("get_mentions", params).mapCatching(::decode).getOrElse { return Result.failure(it) }
            if (events.isEmpty()) return Result.success(emptyList())
            // Without the names the list is still worth showing: rooms and senders fall back to IDs.
            val state = state(events).getOrDefault(emptyMap())
            return Result.success(events.map { resolve(it, state) }.onEach { loaded[it.message.eventId] = it })
        }

        /** The names and avatars of the rooms, and the senders' member events, in one call. */
        private suspend fun state(events: List<Event>): Result<Map<StateKey, JsonObject>> {
            val keys =
                events.map { it.roomId }.distinct().flatMap { room -> ROOM_STATE.map { StateKey(room, it, "") } } +
                    events.map { StateKey(it.roomId, MEMBER, it.sender) }.distinct()
            val params =
                buildJsonObject {
                    put(
                        "keys",
                        buildJsonArray {
                            keys.forEach { key ->
                                add(
                                    buildJsonObject {
                                        put("room_id", JsonPrimitive(key.roomId))
                                        put("type", JsonPrimitive(key.type))
                                        put("state_key", JsonPrimitive(key.stateKey))
                                    },
                                )
                            }
                        },
                    )
                }
            return read("get_specific_room_state", params).mapCatching { data ->
                decode(data).associate { StateKey(it.roomId, it.type, it.stateKey.orEmpty()) to it.content }
            }
        }

        private fun resolve(
            event: Event,
            state: Map<StateKey, JsonObject>,
        ): Notified {
            val member = state[StateKey(event.roomId, MEMBER, event.sender)]
            val sender =
                PushUser(
                    id = event.sender,
                    name = member?.text("displayname")?.takeIf { it.isNotBlank() } ?: localpart(event.sender),
                    avatar = avatarPath(member?.text("avatar_url")),
                )
            val profile = perMessageProfile(event.effectiveContent)
            val face = profile?.takeIf { it.name.isNotBlank() } ?: sender
            val name =
                state[StateKey(event.roomId, NAME, "")]?.text("name")?.takeIf { it.isNotBlank() }
                    ?: state[StateKey(event.roomId, ALIAS, "")]?.text("alias")?.takeIf { it.isNotBlank() }
            val content = event.effectiveContent
            val message =
                PushMessage(
                    timestamp = event.timestamp,
                    eventId = event.eventId,
                    roomId = event.roomId,
                    roomName = name ?: face.name,
                    roomAvatar =
                        if (name == null) {
                            face.avatar
                        } else {
                            avatarPath(state[StateKey(event.roomId, AVATAR, "")]?.text("url"))
                        },
                    isDm = name == null,
                    sender = sender,
                    // Nobody reads it here: who "we" are only matters to a notification's MessagingStyle.
                    self = sender,
                    perMessageProfile = profile,
                    text = event.localContent?.previewText ?: content.text("body").orEmpty(),
                    mention = event.unreadType and HIGHLIGHT != 0,
                )
            return notifiedOf(event, message)
        }

        private suspend fun read(
            command: String,
            params: JsonObject,
        ): Result<JsonElement> =
            when (val result = exec.exec(command, params, ExecMode.Read)) {
                is ExecResult.Ok -> Result.success(result.data)
                is ExecResult.CommandError -> Result.failure(IOException("$command: ${result.message}"))
                is ExecResult.NetworkError -> Result.failure(result.cause)
            }

        /** The events in [json]: a list, or an object holding one under `events`. */
        private fun decode(json: JsonElement?): List<Event> =
            (if (json is JsonObject) json["events"] else json)
                ?.takeIf { it !is JsonNull }
                ?.let { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), it) }
                .orEmpty()

        private data class StateKey(
            val roomId: String,
            val type: String,
            val stateKey: String,
        )

        private companion object {
            const val PAGE = 20
            const val NOTIFY = 0b0010
            const val HIGHLIGHT = 0b0100
            const val MEMBER = "m.room.member"
            const val NAME = "m.room.name"
            const val AVATAR = "m.room.avatar"
            const val ALIAS = "m.room.canonical_alias"
            val ROOM_STATE = listOf(NAME, AVATAR, ALIAS)
        }
    }

/** The per-message profile (MSC4144) a message was sent as, unstable key or stable; null for none. */
private fun perMessageProfile(content: JsonObject): PushUser? {
    val profile =
        (content["com.beeper.per_message_profile"] ?: content["m.per_message_profile"]) as? JsonObject
            ?: return null
    return PushUser(
        id = profile.text("id").orEmpty(),
        name = profile.text("displayname").orEmpty(),
        avatar = avatarPath(profile.text("avatar_url")),
    )
}

/** An mxc:// avatar as a gomuks media path (relative to the server), as an avatar-sized thumbnail. */
internal fun avatarPath(mxc: String?): String? {
    val rest = mxc?.removePrefix("mxc://")?.takeIf { it != mxc } ?: return null
    val (server, id) = rest.split('/').takeIf { it.size == 2 } ?: return null
    return "_gomuks/media/$server/$id?encrypted=false&thumbnail=avatar"
}

private fun localpart(userId: String) = userId.removePrefix("@").substringBefore(':')

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
