package pt.aguiarvieira.xmuks.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.connection.SyncSummarySink
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        private val connection: ForegroundConnection,
        summary: SyncSummarySink,
        store: CredentialStore,
        private val session: SessionRepository,
    ) : ViewModel() {
        val state = connection.state
        val summary = summary.summary
        val server: String =
            store
                .credentials()
                ?.serverUrl
                ?.host
                .orEmpty()

        fun reconnect() = connection.restart()

        fun logout() {
            viewModelScope.launch { session.logout() }
        }
    }
