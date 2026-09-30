package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.timeline.bridgeOf
import pt.aguiarvieira.xmuks.core.database.RoomBridgeEntity
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import kotlin.coroutines.cancellation.CancellationException

/**
 * Learns which rooms are bridged, for the room list's network badges: `m.bridge` state is in no
 * sync gomuks sends (only its changes are), so each room's state is asked for once, then kept.
 *
 * Gentle both ways: only while the stream is live (the app in use), one room at a time with a
 * pause between them, most recent rooms first; only bridge events are kept from each answer, and
 * results are written in batches (every write re-runs the room list's queries). After a login the
 * table is empty, so a whole scan follows; later, only rooms new or whose bridge state changed.
 */
class BridgeScanner(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val scope: CoroutineScope,
    private val pauseMs: Long = PAUSE_MS,
) {
    private val dao = database.syncDao()
    private var job: Job? = null

    /** Rooms whose state couldn't be had this run: not asked again until the next. */
    private val failed = HashSet<String>()

    @Synchronized
    fun streamChanged(live: Boolean) {
        if (!live) {
            job?.cancel()
            job = null
        } else if (job?.isActive != true) {
            failed.clear()
            job = scope.launch { run() }
        }
    }

    private suspend fun run() {
        while (true) {
            val rooms = dao.uncheckedBridgeRooms(BATCH + failed.size).filterNot { it in failed }.take(BATCH)
            if (rooms.isEmpty()) {
                // Nothing to do; a new room or a bridge change will show up here later.
                delay(IDLE_MS)
                continue
            }
            val found = ArrayList<RoomBridgeEntity>(rooms.size)
            try {
                for (roomId in rooms) {
                    val events = bridgeEvents(roomId)
                    if (events == null) {
                        failed += roomId
                    } else {
                        val bridge = bridgeOf(events)
                        found += RoomBridgeEntity(roomId, bridge?.protocol, bridge?.protocolAvatarMxc)
                    }
                    delay(pauseMs)
                }
            } finally {
                // Keep what was learnt even when cancelled midway (the app left the foreground).
                if (found.isNotEmpty()) scope.launch { dao.upsertBridges(found) }
            }
        }
    }

    /** The room's bridge state events (members left out of the request); null if gomuks couldn't say. */
    private suspend fun bridgeEvents(roomId: String): List<Event>? {
        val params =
            buildJsonObject {
                put("room_id", JsonPrimitive(roomId))
                put("include_members", JsonPrimitive(false))
            }
        val result =
            try {
                exec.exec("get_room_state", params, ExecMode.Read) as? ExecResult.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null
        val state = result.data as? JsonArray ?: return null
        return state
            .filter { ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.content in BRIDGE_TYPES }
            .mapNotNull { runCatching { GomuksJson.decodeFromJsonElement(Event.serializer(), it) }.getOrNull() }
    }

    private companion object {
        const val BATCH = 20
        const val PAUSE_MS = 250L
        const val IDLE_MS = 60_000L
        val BRIDGE_TYPES = setOf("m.bridge", "uk.half-shot.bridge")
    }
}
