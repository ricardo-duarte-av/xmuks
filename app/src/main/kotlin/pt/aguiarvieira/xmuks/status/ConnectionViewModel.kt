package pt.aguiarvieira.xmuks.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.StreamStats
import pt.aguiarvieira.xmuks.core.data.connection.StreamStatsTracker
import pt.aguiarvieira.xmuks.core.database.CacheCounts
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import javax.inject.Inject

/** What the M2 status screen shows: the cache (from the database) and the live stream. */
data class StatusUi(
    val counts: CacheCounts = CacheCounts(0, 0, 0, 0, 0),
    val userId: String? = null,
    val lastServerTs: Long = 0,
    val lastFullSyncAt: Long = 0,
    val stats: StreamStats = StreamStats(),
)

@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        private val connection: ForegroundConnection,
        stats: StreamStatsTracker,
        database: XmuksDatabase,
        store: CredentialStore,
        private val session: SessionRepository,
    ) : ViewModel() {
        val state = connection.state
        val server: String =
            store
                .credentials()
                ?.serverUrl
                ?.host
                .orEmpty()

        private val dao = database.roomListDao()
        val status: StateFlow<StatusUi> =
            combine(dao.counts(), dao.meta(), stats.stats) { counts, meta, streamStats ->
                StatusUi(counts, meta?.userId, meta?.lastServerTs ?: 0, meta?.lastFullSyncAt ?: 0, streamStats)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatusUi())

        fun reconnect() = connection.restart()

        fun logout() {
            viewModelScope.launch { session.logout() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
