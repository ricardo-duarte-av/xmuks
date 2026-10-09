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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.emoji.RoomEmoji
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.toTimelineItem
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.outbox.toTimelineItems
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
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
    private val outbox: Outbox,
    private val uploads: MediaSender,
    private val scope: CoroutineScope,
    prefs: PreferenceStore? = null,
    /** Rooms whose state a sync changed: this room's is fetched again when it's among them. */
    private val stateChanges: Flow<String> = emptyFlow(),
) {
    private val dao = database.roomListDao()

    /** gomuks' preferences as they apply in this room. */
    val preferences: Flow<PrefLayers> = prefs?.layers(roomId) ?: flowOf(PrefLayers.EMPTY)
    private val profiles = MutableStateFlow<Map<String, MemberProfile>>(emptyMap())
    private val requested = HashSet<String>()
    private val profileLock = Mutex()

    private val _state = MutableStateFlow<List<Event>>(emptyList())

    /** Current room state events (no members), as of this open. */
    val state: StateFlow<List<Event>> = _state.asStateFlow()

    /** What this room is bridged to, if it is. */
    val bridge: Flow<BridgeInfo?> = state.map(::bridgeOf).distinctUntilChanged()

    /** Polls here: their votes, and starting, answering and ending them. */
    val polls = RoomPolls(roomId, exec, outbox, scope)

    val snapshot: Flow<TimelineSnapshot> = polls.withVotes(flow { emitAll(store.observe(roomId)) })

    /**
     * What the timeline shows; rebuilt off the main thread whenever the timeline or a profile
     * changes. Messages still in our outbox come last: they're newer than anything gomuks has.
     */
    val items: Flow<List<TimelineItem>> =
        combine(
            itemsOf(snapshot),
            outbox.observe(roomId),
            uploads.observe(roomId),
            dao.meta()
        ) { live, unsent, uploading, meta ->
            val me = meta?.userId
            if ((unsent.isEmpty() && uploading.isEmpty()) || me == null) {
                live
            } else {
                val name = meta.displayName ?: localpart(me)
                // Uploads first: they started before the messages their outbox entries became.
                live + uploading.map { it.toTimelineItem(me, name) } + unsent.toTimelineItems(me, name)
            }
        }

    /**
     * The commands usable here: gomuks' built-ins, the text prefixes it understands, and those
     * the room's bots publish in state (refreshed with the room state on every open).
     */
    val commands: Flow<List<BotCommand>> =
        state.map { events ->
            val bots =
                events
                    .filter { it.type == BotCommand.STATE_TYPE && it.redactedBy == null }
                    .mapNotNull { BotCommand.parse(it.content, it.sender) }
            BUILT_IN_COMMANDS + bots
        }

    /** Everything this session sends: messages, deletions, receipts, typing. */
    val writer = RoomWriter(roomId, exec, outbox, preferences)

    /** Emoji and sticker packs usable here, recent emoji, reacting. */
    val emoji =
        RoomEmoji(
            roomId,
            state,
            dao,
            exec,
            writer,
            preferences.map { it.get(Prefs.showRoomEmojiPacks) }.distinctUntilChanged()
        )

    /**
     * Every version of [eventId], oldest first: the original, then each edit gomuks has
     * (`get_related_events`, m.replace). Null when gomuks can't answer.
     */
    suspend fun editHistory(eventId: String): List<MessageVersion>? {
        val original = getEvent(eventId, unredact = false) ?: return null
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("relation_type", JsonPrimitive("m.replace"))
            }
        val result = exec.exec("get_related_events", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        // Only edits by the original's sender count (anyone can send an m.replace; it's ignored).
        val edits = decodeEvents(result).orEmpty().filter { it.sender == original.sender }.sortedBy { it.timestamp }
        return listOf(original.toVersion(edit = false)) + edits.map { it.toVersion(edit = true) }
    }

    /**
     * What a deleted message said: gomuks asks the homeserver for the unredacted event (allowed for
     * room moderators). Null when it isn't available.
     */
    suspend fun deletedContent(eventId: String): MessageVersion? {
        val event = getEvent(eventId, unredact = true) ?: return null
        return event.toVersion(edit = false).takeIf { !it.body.isNullOrBlank() || it.html != null }
    }

    private suspend fun getEvent(
        eventId: String,
        unredact: Boolean,
    ): Event? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("event_id", JsonPrimitive(eventId))
                put("unredact", JsonPrimitive(unredact))
            }
        val result = exec.exec("get_event", params, ExecMode.Read) as? ExecResult.Ok ?: return null
        return runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), result.data) }.getOrNull()
    }

    /** Items for any snapshot of this room (the live one, or an event context), with its profiles. */
    fun itemsOf(snapshots: Flow<TimelineSnapshot>): Flow<List<TimelineItem>> =
        combine(
            snapshots,
            profiles,
            dao.meta().map { it?.userId }.distinctUntilChanged(),
            preferences.map(TimelineOptions::of).distinctUntilChanged(),
        ) { snap, known, me, options ->
            resolveMissing(snap, known)
            TimelineItemBuilder(me, options).build(snap, known)
        }.flowOn(Dispatchers.Default)

    /** Our read marker here (`m.fully_read`): the last event read; gomuks' mark_read moves it. */
    suspend fun readMarker(): String? =
        dao
            .accountData(roomId, "m.fully_read")
            .first()
            ?.let { runCatching { GomuksJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?.str("event_id")

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

    /** What's pinned here (`m.room.pinned_events`), and whether we may change it. */
    val pins: Flow<Pins> =
        combine(state, dao.meta().map { it?.userId }.distinctUntilChanged()) { events, me ->
            val pinned =
                events.firstOrNull { it.type == Pins.TYPE && it.stateKey == "" }?.content?.get("pinned") as? JsonArray
            val ids = pinned.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val levels = RoomInfo.parse(roomId, events).powerLevels
            Pins(ids, me != null && levels.canSetState(me, Pins.TYPE))
        }.distinctUntilChanged()

    /** Whether we may be in a call here (true until the room's state says otherwise). */
    val canCall: Flow<Boolean> =
        combine(state, dao.meta().map { it?.userId }.distinctUntilChanged()) { events, me ->
            me == null || events.isEmpty() || RoomInfo.parse(roomId, events).powerLevels.canCall(me)
        }.distinctUntilChanged()

    /** Pinning and unpinning, and the pinned messages themselves. */
    val pinning = RoomPinning(roomId, exec, pins, { getEvent(it, unredact = false) }, { itemsOf(flowOf(it)).first() })

    /** One of this room's threads, from its root. */
    fun thread(rootId: String) = ThreadTimeline(roomId, rootId, exec, snapshot)

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
        scope.launch { stateChanges.filter { it == roomId }.collect { fetchState() } }
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
        val BUILT_IN_COMMANDS by lazy { BotCommand.builtIns() + BotCommand.textPrefixes }
    }
}

/** One version of a message: when it was written and what it said. */
data class MessageVersion(
    val timestamp: Long,
    val body: String?,
    val html: String?,
    val edit: Boolean,
)

private fun Event.toVersion(edit: Boolean): MessageVersion {
    val content = effectiveContent
    // An edit's text is its m.new_content; its body carries the "* " fallback.
    val shown = if (edit) (content["m.new_content"] as? JsonObject) ?: content else content
    val body = (shown["body"] as? JsonPrimitive)?.contentOrNull
    val html = localContent?.sanitizedHtml?.takeIf { it.isNotBlank() && localContent?.wasPlaintext != true }
    return MessageVersion(timestamp, body, html, edit)
}

/** What a reply points at: the original's event ID and who wrote it (they get mentioned). */
data class ReplyTarget(
    val eventId: String,
    val sender: String,
    /** Sent into this thread (MSC3440): [eventId] is then the message answered in it. */
    val threadRoot: String? = null,
    /**
     * In a thread without answering anything in particular: [eventId] is its latest message, a
     * reply only for clients without threads (and nobody is pinged).
     */
    val fallback: Boolean = false,
)

/** Creates sessions for rooms being opened. */
class RoomSessions(
    private val store: TimelineStore,
    private val exec: ExecClient,
    private val database: XmuksDatabase,
    private val outbox: Outbox,
    private val uploads: MediaSender,
    private val scope: CoroutineScope,
    private val prefs: PreferenceStore? = null,
    private val stateChanges: Flow<String> = emptyFlow(),
) {
    fun open(roomId: String) = RoomSession(roomId, store, exec, database, outbox, uploads, scope, prefs, stateChanges)

    /** Stops our typing notification in [roomId], outliving whoever asked. */
    fun stopTyping(roomId: String) {
        scope.launch { RoomWriter(roomId, exec, outbox).setTyping(0) }
    }
}
