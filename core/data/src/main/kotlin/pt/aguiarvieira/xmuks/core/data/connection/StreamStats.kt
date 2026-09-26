package pt.aguiarvieira.xmuks.core.data.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.aguiarvieira.xmuks.core.protocol.GomuksEvent
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame

/** Per-connection stream diagnostics. The data itself lives in the database. */
data class StreamStats(
    /** Time from connecting to init_complete on the current connection. */
    val initialSyncMs: Long? = null,
    /** Whether the current connection started with a catch-up rather than a full snapshot. */
    val catchup: Boolean = false,
    val liveEvents: Int = 0,
    /** gomuks' own sync state against the homeserver ("ok", or its error). */
    val homeserverSync: String? = null,
)

class StreamStatsTracker(
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountScoped {
    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private var connectedAt = 0L
    private var initialised = false

    override suspend fun clearAccountData() = reset()

    @Synchronized
    private fun reset() {
        initialised = false
        _stats.value = StreamStats()
    }

    @Synchronized
    fun accept(frame: GomuksFrame) {
        when (val event = frame.event) {
            is GomuksEvent.RunId -> {
                connectedAt = clock()
                initialised = false
                _stats.update { it.copy(initialSyncMs = null, catchup = false) }
            }

            is GomuksEvent.SyncStatus -> {
                _stats.update { it.copy(homeserverSync = event.error ?: event.type) }
            }

            is GomuksEvent.Sync -> {
                _stats.update {
                    if (initialised) it.copy(liveEvents = it.liveEvents + 1) else it.copy(catchup = event.sync.catchup)
                }
            }

            GomuksEvent.InitComplete -> {
                initialised = true
                _stats.update { it.copy(initialSyncMs = clock() - connectedAt) }
            }

            else -> {
                if (initialised) _stats.update { it.copy(liveEvents = it.liveEvents + 1) }
            }
        }
    }
}
