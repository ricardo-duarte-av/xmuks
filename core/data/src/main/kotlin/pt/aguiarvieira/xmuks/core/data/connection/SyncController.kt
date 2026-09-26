package pt.aguiarvieira.xmuks.core.data.connection

import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor

/** User-initiated sync actions. */
class SyncController(
    private val ingestor: SyncIngestor,
    private val connection: ForegroundConnection,
) {
    val state = connection.state

    /** Pull-to-refresh: reconnect for a full snapshot, which also sweeps anything stale. */
    suspend fun refresh() {
        ingestor.requestFullResync()
        connection.restart()
    }
}
