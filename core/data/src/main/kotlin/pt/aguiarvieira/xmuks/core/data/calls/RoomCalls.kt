package pt.aguiarvieira.xmuks.core.data.calls

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.sync.recordCallMember
import pt.aguiarvieira.xmuks.core.database.CallMemberEntity
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTypes

/** Someone in a room's call, as far as the room list and header need to know. */
data class RoomCallMember(
    val userId: String,
    val deviceId: String,
    val intent: String?,
    val joinedAt: Long,
)

/** A call going on in a room. */
data class RoomCall(
    val roomId: String,
    val members: List<RoomCallMember>,
    /** This app is in it. */
    val joinedHere: Boolean = false,
    /**
     * Our Matrix device is in it, but not through this app: another client of the same gomuks
     * (gomuks web's Element Call, another xmuks). Joining here takes the device's place.
     */
    val joinedElsewhereOnDevice: Boolean = false,
) {
    val startedAt: Long get() = members.minOf { it.joinedAt }

    /** Someone joined meaning to show video. */
    val isVideo: Boolean get() = members.any { it.intent == RtcTypes.INTENT_VIDEO }

    /** How many people (not devices) are in it. */
    val people: Int get() = members.distinctBy { it.userId }.size

    fun includes(
        userId: String,
        deviceId: String,
    ) = members.any { it.userId == userId && it.deviceId == deviceId }
}

/**
 * Which rooms have a call going on, from the call memberships sync has delivered (persisted, so
 * known at once after a restart). Memberships lapse on their own — legacy `expires`, sticky
 * duration — so the answer is re-evaluated as time passes, not only when the table changes.
 */
class RoomCalls(
    private val database: XmuksDatabase,
    private val exec: ExecClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableInCall = MutableStateFlow<String?>(null)

    /** The room whose call this app is in (set by the call manager), or null. */
    val inCall: StateFlow<String?> = mutableInCall.asStateFlow()

    fun setInCall(roomId: String?) {
        mutableInCall.value = roomId
    }

    private val dao = database.roomListDao()

    /** Rooms with a call, by room id. */
    fun all(): Flow<Map<String, RoomCall>> =
        combine(dao.callMembers(), ticks()) { rows, now ->
            rows
                .filter { it.expiresAt > now }
                .groupBy { it.roomId }
                .mapValues { (roomId, members) -> RoomCall(roomId, members.map(::toMember)) }
        }.distinctUntilChanged()

    /** [roomId]'s call, or null when there is none. */
    fun of(roomId: String): Flow<RoomCall?> =
        combine(dao.callMembers(roomId), dao.meta(), ticks(), inCall) { rows, meta, now, inCall ->
            rows
                .filter { it.expiresAt > now }
                .takeIf { it.isNotEmpty() }
                ?.let { live ->
                    val here = inCall == roomId
                    val device = live.any { it.userId == meta?.userId && it.deviceId == meta.deviceId }
                    RoomCall(roomId, live.map(::toMember), joinedHere = here, joinedElsewhereOnDevice = device && !here)
                }
        }.distinctUntilChanged()

    /**
     * Brings [roomId]'s rows up to date from a full read of its state and sticky events (what a call
     * screen loads anyway): sync deltas can't tell us about calls that were going on before them.
     */
    suspend fun refresh(
        roomId: String,
        events: List<Event>,
    ) {
        val sync = database.syncDao()
        database.withWriteTransaction {
            sync.deleteCallMembersOf(listOf(roomId))
            events
                .filter { it.effectiveType == RtcTypes.LEGACY_MEMBER || it.effectiveType == RtcTypes.STICKY_MEMBER }
                .sortedBy { it.timestamp }
                .forEach { recordCallMember(sync, roomId, it) }
        }
    }

    /** Reads [roomId]'s state and sticky events from gomuks and [refresh]es from them (a room was opened). */
    suspend fun load(roomId: String) {
        val room = buildJsonObject { put("room_id", JsonPrimitive(roomId)) }
        val stateParams =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("include_members", JsonPrimitive(false))
                put("fetch_members", JsonPrimitive(false))
                put("refetch", JsonPrimitive(false))
            }
        val state = events(exec.exec("get_room_state", stateParams, ExecMode.Read)) ?: return
        val sticky = events(exec.exec("get_sticky_events", room, ExecMode.Read)).orEmpty()
        refresh(roomId, state + sticky)
    }

    private fun events(result: ExecResult): List<Event>? =
        (result as? ExecResult.Ok)?.data?.let { data ->
            runCatching { GomuksJson.decodeFromJsonElement(ListSerializer(Event.serializer()), data) }.getOrNull()
        }

    private fun ticks() =
        flow {
            while (true) {
                emit(clock())
                delay(TICK_MS)
            }
        }

    private fun toMember(row: CallMemberEntity) = RoomCallMember(row.userId, row.deviceId, row.intent, row.createdTs)

    private companion object {
        const val TICK_MS = 30_000L
    }
}
