package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * Events fetched one by one (`get_event`), kept in memory only, the most recently used [capacity]
 * of them. An event's id never changes what it says, but gomuks' copy carries what came after it
 * (reactions, redaction, decryption, its latest edit's row), so what's [cached] is for showing
 * at once while [fetch] gets the current copy.
 */
class EventCache(
    private val exec: ExecClient,
    private val capacity: Int = CAPACITY,
) : AccountScoped {
    private data class Key(
        val roomId: String,
        val eventId: String,
        val unredact: Boolean,
    )

    private val events =
        object : LinkedHashMap<Key, Event>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Event>) = size > capacity
        }

    /** The copy last fetched, if it's still held. */
    fun cached(
        roomId: String,
        eventId: String,
        unredact: Boolean = false,
    ): Event? = synchronized(events) { events[Key(roomId, eventId, unredact)] }

    /** gomuks' current copy, kept for next time; null (and nothing kept) when it can't be had. */
    suspend fun fetch(
        roomId: String,
        eventId: String,
        unredact: Boolean = false,
    ): Event? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("unredact", JsonPrimitive(unredact))
            }
        val result = exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        val event =
            runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), result.data) }.getOrNull()
                ?: return null
        synchronized(events) { events[Key(roomId, eventId, unredact)] = event }
        return event
    }

    override suspend fun clearAccountData() = synchronized(events) { events.clear() }

    private companion object {
        const val CAPACITY = 500
        const val LOAD_FACTOR = 0.75f
    }
}
