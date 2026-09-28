package pt.aguiarvieira.xmuks.core.data.emoji

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.RoomWriter
import pt.aguiarvieira.xmuks.core.database.RoomListDao
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * Emoji and stickers for one open room: the packs we can use here (our personal pack, the room's
 * own, and those we subscribed to elsewhere), recently used emoji, and reacting.
 */
class RoomEmoji(
    private val roomId: String,
    private val roomState: Flow<List<Event>>,
    private val dao: RoomListDao,
    private val exec: ExecClient,
    private val writer: RoomWriter,
) {
    /** Subscribed packs' state events, fetched from their rooms (room ID + state key → event). */
    private val subscribedStates = MutableStateFlow<Map<Pair<String, String>, Event>>(emptyMap())
    private val fetched = HashSet<Pair<String, String>>()

    private fun global(type: String) = dao.accountData("", type).map { it?.let(::parseObject) }

    private val subscriptions: Flow<Set<Pair<String, String>>> =
        combine(global(ImagePack.SUBSCRIPTIONS), global(ImagePack.SUBSCRIPTIONS_LEGACY)) { new, legacy ->
            // The new event wins when present, like gomuks web.
            ((new ?: legacy)?.get("rooms") as? JsonObject)
                ?.flatMap { (room, keys) -> (keys as? JsonObject)?.keys?.map { room to it }.orEmpty() }
                ?.toSet()
                .orEmpty()
        }.distinctUntilChanged()

    /** Every pack usable here: personal first, then this room's, then subscribed ones. */
    val packs: Flow<List<ImagePack>> =
        combine(
            global(ImagePack.PERSONAL),
            global(ImagePack.PERSONAL_LEGACY),
            roomState,
            subscriptions,
            subscribedStates,
        ) { personal, personalLegacy, state, subscribed, others ->
            fetchMissing(subscribed)
            buildList {
                (personal ?: personalLegacy)
                    ?.let {
                        ImagePack.parse(
                            it,
                            "personal",
                            "Personal",
                            ImagePack.Source.Personal
                        )
                    }?.let(::add)
                state.filter { it.type == ImagePack.ROOM || it.type == ImagePack.ROOM_LEGACY }.forEach { evt ->
                    val key = evt.stateKey.orEmpty()
                    val source = ImagePack.Source.Room(roomId, key, subscribed = (roomId to key) in subscribed)
                    ImagePack.parse(evt.content, "$roomId/$key", null, source)?.let(::add)
                }
                others.filterKeys { it in subscribed && it.first != roomId }.forEach { (key, evt) ->
                    ImagePack
                        .parse(
                            evt.content,
                            "${key.first}/${key.second}",
                            null,
                            ImagePack.Source.Room(key.first, key.second, true)
                        )?.let(::add)
                }
            }.distinctBy { it.id }
        }

    /** Recently used emoji keys (Unicode or `mxc://`), most used first (`io.element.recent_emoji`). */
    val recent: Flow<List<String>> =
        global(RECENT).map { content ->
            (content?.get("recent_emoji") as? JsonArray)
                ?.mapNotNull { entry ->
                    ((entry as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
                }.orEmpty()
        }

    /** Adds our [key] reaction to [target], or takes it back if it's already ours. */
    suspend fun toggleReaction(
        target: String,
        key: String,
        existing: Reaction?,
        shortcode: String? = null,
    ) {
        if (existing?.mine == true) {
            val ids = existing.myEventId?.let(::listOf) ?: myReactionIds(target, key)
            ids.forEach { writer.redact(it) }
        } else {
            writer.react(target, key, shortcode)
            bumpRecent(key)
        }
    }

    /** Subscribes to (or drops) a room's pack, keeping both the new and legacy account data in step. */
    suspend fun setSubscribed(
        pack: ImagePack.Source.Room,
        subscribe: Boolean,
    ) {
        val newContent = dao.accountData("", ImagePack.SUBSCRIPTIONS).first()?.let(::parseObject)
        val current = newContent ?: dao.accountData("", ImagePack.SUBSCRIPTIONS_LEGACY).first()?.let(::parseObject)
        val rooms = (current?.get("rooms") as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val keys = (rooms[pack.roomId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (subscribe) keys[pack.stateKey] = JsonObject(emptyMap()) else keys.remove(pack.stateKey)
        if (keys.isEmpty()) rooms.remove(pack.roomId) else rooms[pack.roomId] = JsonObject(keys)
        val updated = JsonObject((current ?: JsonObject(emptyMap())) + ("rooms" to JsonObject(rooms)))
        if (newContent != null) writer.setAccountData(ImagePack.SUBSCRIPTIONS, updated)
        writer.setAccountData(ImagePack.SUBSCRIPTIONS_LEGACY, updated)
    }

    /** Moves [key] to the front of the recently used list (as gomuks web does, max 100). */
    suspend fun bumpRecent(key: String) {
        val content = dao.accountData("", RECENT).first()?.let(::parseObject)
        val entries =
            (content?.get("recent_emoji") as? JsonArray)
                ?.mapNotNull { it as? JsonArray }
                ?.mapNotNull { e ->
                    ((e.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null) to
                        ((e.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 1)
                }.orEmpty()
        val count = (entries.firstOrNull { it.first == key }?.second ?: 0) + 1
        val updated = (listOf(key to count) + entries.filter { it.first != key }).take(MAX_RECENT)
        writer.setAccountData(
            RECENT,
            buildJsonObject {
                put(
                    "recent_emoji",
                    buildJsonArray {
                        updated.forEach { (k, n) ->
                            add(
                                buildJsonArray {
                                    add(JsonPrimitive(k))
                                    add(JsonPrimitive(n))
                                }
                            )
                        }
                    }
                )
            },
        )
    }

    /** Our reaction events with [key] on [target], when they aren't in the loaded timeline. */
    private suspend fun myReactionIds(
        target: String,
        key: String,
    ): List<String> {
        val me = dao.meta().first()?.userId ?: return emptyList()
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(target))
                put("relation_type", JsonPrimitive("m.annotation"))
            }
        val result = exec.exec("get_related_events", params, ExecMode.Read) as? ExecResult.Ok ?: return emptyList()
        return decodeEvents(result.data)
            .filter { it.sender == me && it.redactedBy == null }
            .filter { ((it.content["m.relates_to"] as? JsonObject)?.get("key") as? JsonPrimitive)?.content == key }
            .map { it.eventId }
    }

    /** Loads the state of subscribed packs we haven't fetched yet (both event types, one request). */
    private suspend fun fetchMissing(subscribed: Set<Pair<String, String>>) {
        val missing = subscribed.filter { it.first != roomId && fetched.add(it) }
        if (missing.isEmpty()) return
        val keys =
            buildJsonArray {
                missing.forEach { (room, key) ->
                    listOf(ImagePack.ROOM, ImagePack.ROOM_LEGACY).forEach { type ->
                        add(
                            buildJsonObject {
                                put("room_id", JsonPrimitive(room))
                                put("type", JsonPrimitive(type))
                                put("state_key", JsonPrimitive(key))
                            },
                        )
                    }
                }
            }
        val result =
            exec.exec("get_specific_room_state", buildJsonObject { put("keys", keys) }, ExecMode.Read) as? ExecResult.Ok
                ?: return
        val found =
            decodeEvents(result.data)
                .sortedBy { if (it.type == ImagePack.ROOM) 1 else 0 } // the new type wins over the legacy one
                .associateBy { it.roomId to it.stateKey.orEmpty() }
        subscribedStates.value = subscribedStates.value + found
    }

    private fun decodeEvents(data: JsonElement): List<Event> =
        runCatching {
            GomuksJson.decodeFromJsonElement(
                ListSerializer(Event.serializer()),
                data
            )
        }.getOrDefault(emptyList())

    private fun parseObject(json: String): JsonObject? =
        runCatching {
            GomuksJson.parseToJsonElement(json).jsonObject
        }.getOrNull()

    private companion object {
        const val RECENT = "io.element.recent_emoji"
        const val MAX_RECENT = 100
    }
}
