package pt.aguiarvieira.xmuks.core.data.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.network.ResumeStore
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame
import pt.aguiarvieira.xmuks.core.protocol.SyncComplete

/** What the stream has told us so far — enough to prove the pipeline end to end. */
data class SyncSummary(
    val userId: String? = null,
    val rooms: Int = 0,
    val spaces: Int = 0,
    val topLevelSpaces: Int = 0,
    val dms: Int = 0,
    val lastServerTs: Long = 0,
    val initialSyncMs: Long? = null,
    val catchup: Boolean = false,
    val liveEvents: Int = 0,
    val homeserverSync: String? = null,
)

/**
 * M1 stand-in for the database: folds frames into a [SyncSummary]. M2 replaces this sink with the
 * Room-backed ingestor; the connection code does not change.
 */
class SyncSummarySink(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _summary = MutableStateFlow(SyncSummary())
    val summary: StateFlow<SyncSummary> = _summary.asStateFlow()

    private val roomIds = HashSet<String>()
    private val spaceIds = HashSet<String>()
    private val dmIds = HashSet<String>()
    private var connectedAt = 0L
    private var initialised = false

    @Synchronized
    fun accept(frame: GomuksFrame) {
        when (val event = frame.event) {
            is GomuksEvent.RunId -> {
                connectedAt = clock()
                initialised = false
            }

            is GomuksEvent.ClientState -> {
                _summary.update { it.copy(userId = event.userId) }
            }

            is GomuksEvent.SyncStatus -> {
                _summary.update { it.copy(homeserverSync = event.error ?: event.type) }
            }

            is GomuksEvent.Sync -> {
                applySync(event.sync)
            }

            GomuksEvent.InitComplete -> {
                initialised = true
                _summary.update { it.copy(initialSyncMs = clock() - connectedAt) }
            }

            else -> {
                if (initialised) _summary.update { it.copy(liveEvents = it.liveEvents + 1) }
            }
        }
    }

    private fun applySync(sync: SyncComplete) {
        if (sync.clearState) {
            roomIds.clear()
            spaceIds.clear()
            dmIds.clear()
        }
        sync.rooms.forEach { (id, room) ->
            roomIds += id
            val meta = room.meta ?: return@forEach
            if (meta.isSpace) spaceIds += id
            if (meta.dmUserId != null) dmIds += id
        }
        sync.leftRooms.forEach {
            roomIds -= it
            spaceIds -= it
            dmIds -= it
        }
        _summary.update {
            it.copy(
                rooms = roomIds.size,
                spaces = spaceIds.size,
                dms = dmIds.size,
                topLevelSpaces = sync.topLevelSpaces?.size ?: it.topLevelSpaces,
                lastServerTs = maxOf(it.lastServerTs, sync.serverTimestamp),
                catchup = if (initialised) it.catchup else sync.catchup,
                liveEvents = if (initialised) it.liveEvents + 1 else it.liveEvents,
            )
        }
    }
}

/**
 * Resume state kept for the life of the process. Catch-up (`last_server_ts`) is only valid while
 * the data it builds on is still here; with an in-memory M1 sink that means in-memory too. M2 moves
 * this next to the database, so it persists exactly as long as the cache does.
 */
class InMemoryResumeStore : ResumeStore {
    @Volatile private var point = ResumePoint()

    override suspend fun load() = point

    override suspend fun save(point: ResumePoint) {
        this.point = point
    }
}
