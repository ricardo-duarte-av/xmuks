package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.EventContextResponse
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * One open room: its timeline (from [TimelineStore]), its state (fetched fresh on every open, not
 * persisted, members excluded) and the per-room profiles of the people in view — resolved lazily
 * with batched `get_specific_room_state`, seeded from the member events already in the database.
 */
class RoomSession(
    val roomId: String,
    private val store: TimelineStore,
    private val exec: ExecClient,
    private val database: XmuksDatabase,
    private val scope: CoroutineScope,
) {
    private val dao = database.roomListDao()
    private val profiles = MutableStateFlow<Map<String, MemberProfile>>(emptyMap())
    private val requested = HashSet<String>()
    private val profileLock = Mutex()

    private val _state = MutableStateFlow<List<Event>>(emptyList())

    /** Current room state events (no members), as of this open. */
    val state: StateFlow<List<Event>> = _state.asStateFlow()

    val snapshot: Flow<TimelineSnapshot> = flow { emitAll(store.observe(roomId)) }

    /** What the timeline shows; rebuilt off the main thread whenever the timeline or a profile changes. */
    val items: Flow<List<TimelineItem>> = itemsOf(snapshot)

    /** Items for any snapshot of this room (the live one, or an event context), with its profiles. */
    fun itemsOf(snapshots: Flow<TimelineSnapshot>): Flow<List<TimelineItem>> =
        combine(snapshots, profiles, dao.meta().map { it?.userId }.distinctUntilChanged()) { snap, known, me ->
            resolveMissing(snap, known)
            TimelineItemBuilder(me).build(snap, known)
        }.flowOn(Dispatchers.Default)

    /**
     * A detached window around [eventId] (`get_event_context`) for jumping to something older than
     * the loaded timeline. Not live and not kept; null when gomuks can't get it.
     */
    suspend fun eventContext(eventId: String): TimelineSnapshot? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("limit", JsonPrimitive(CONTEXT_LIMIT))
            }
        val result = exec.exec("get_event_context", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        val response =
            runCatching { GomuksJson.decodeFromJsonElement(EventContextResponse.serializer(), result.data) }.getOrNull()
        val target = response?.event ?: return null
        // Events gomuks hasn't stored carry rowid 0: give them unique stand-ins so they can be keyed.
        var standIn = -1L
        val keyed = { e: Event -> if (e.rowId != 0L) e else e.copy(rowId = standIn--) }
        val events = (response.before.asReversed() + target + response.after).map(keyed)
        val related = response.relatedEvents.map(keyed)
        return TimelineSnapshot(
            roomId = roomId,
            events = events,
            eventsByRowId = (related + events).associateBy { it.rowId },
            hasMoreBefore = false,
            loaded = true,
        )
    }

    /** Display names of whoever is typing (not us). */
    val typing: Flow<List<String>> =
        combine(
            store.typing.map { it[roomId].orEmpty() }.distinctUntilChanged(),
            profiles,
            dao.meta()
        ) { users, known, meta ->
            users.filter { it != meta?.userId }.map { known[it]?.displayName ?: localpart(it) }
        }

    suspend fun open() {
        seedProfiles()
        scope.launch { store.open(roomId) }
        scope.launch { fetchState() }
    }

    suspend fun loadOlder() = store.loadOlder(roomId)

    private suspend fun seedProfiles() {
        val seed =
            dao.memberEvents(roomId).associate { row ->
                row.userId to
                    parseMember(GomuksJson.parseToJsonElement(row.content).jsonObject)
            }
        profiles.value = seed + profiles.value
    }

    private suspend fun fetchState() {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("include_members", JsonPrimitive(false))
                put("fetch_members", JsonPrimitive(false))
                put("refetch", JsonPrimitive(false))
            }
        val result = exec.exec("get_room_state", params, ExecMode.Read) as? ExecResult.Ok ?: return
        _state.value = decodeEvents(result) ?: return
    }

    /** Senders, reply authors and readers we have no name for yet: one batched request. */
    private fun resolveMissing(
        snap: TimelineSnapshot,
        known: Map<String, MemberProfile>,
    ) {
        val wanted =
            buildSet {
                snap.eventsByRowId.values.forEach { add(it.sender) }
                snap.receiptsByEventId.values
                    .flatten()
                    .forEach { add(it.userId) }
            } - known.keys
        if (wanted.isEmpty()) return
        scope.launch {
            val batch = profileLock.withLock { (wanted - requested).also { requested.addAll(it) } }
            if (batch.isNotEmpty()) fetchMembers(batch)
        }
    }

    private suspend fun fetchMembers(userIds: Set<String>) {
        userIds.chunked(MEMBER_BATCH).forEach { chunk ->
            val keys =
                JsonArray(
                    chunk.map { user ->
                        buildJsonObject {
                            put("room_id", JsonPrimitive(roomId))
                            put("type", JsonPrimitive("m.room.member"))
                            put("state_key", JsonPrimitive(user))
                        }
                    },
                )
            val result =
                exec.exec(
                    "get_specific_room_state",
                    buildJsonObject {
                        put("keys", keys)
                    },
                    ExecMode.Read
                ) as? ExecResult.Ok
            val found =
                result?.let(::decodeEvents).orEmpty().mapNotNull { e ->
                    e.stateKey?.let {
                        it to
                            parseMember(e.content)
                    }
                }
            // Unknown users stay unresolved (localpart); they're in `requested`, so not asked again.
            if (found.isNotEmpty()) profiles.value = profiles.value + found
        }
    }

    private fun decodeEvents(result: ExecResult.Ok): List<Event>? =
        runCatching { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), result.data) }.getOrNull()

    private fun parseMember(content: JsonObject) =
        MemberProfile(
            displayName = (content["displayname"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            avatarMxc = (content["avatar_url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("mxc://") },
        )

    private companion object {
        const val MEMBER_BATCH = 50
        const val CONTEXT_LIMIT = 30
    }
}

/** Creates sessions for rooms being opened. */
class RoomSessions(
    private val store: TimelineStore,
    private val exec: ExecClient,
    private val database: XmuksDatabase,
    private val scope: CoroutineScope,
) {
    fun open(roomId: String) = RoomSession(roomId, store, exec, database, scope)
}
