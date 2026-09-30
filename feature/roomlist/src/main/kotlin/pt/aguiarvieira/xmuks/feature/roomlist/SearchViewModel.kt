package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.FoundEvent
import pt.aguiarvieira.xmuks.core.data.rooms.MessageSearch
import pt.aguiarvieira.xmuks.core.data.rooms.SearchQuery

/** The search as it stands: where and how, what was found, and whether more is coming. */
data class SearchState(
    /** The room it was opened from, if any; [SearchQuery.roomId] is it while "this room" is on. */
    val openedIn: String? = null,
    val query: SearchQuery = SearchQuery(""),
    val hits: List<FoundEvent> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel(assistedFactory = SearchViewModel.Factory::class)
class SearchViewModel
    @AssistedInject
    constructor(
        @Assisted roomId: String?,
        private val search: MessageSearch,
        val media: MediaUrls,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String?): SearchViewModel
        }

        val text = TextFieldState()
        private val _state = MutableStateFlow(SearchState(openedIn = roomId, query = SearchQuery("", roomId)))
        val state: StateFlow<SearchState> = _state.asStateFlow()
        private var running: Job? = null

        init {
            viewModelScope.launch {
                snapshotFlow { text.text.toString().trim() }.distinctUntilChanged().collectLatest { typed ->
                    delay(DEBOUNCE_MS)
                    change { it.copy(text = typed) }
                }
            }
        }

        fun thisRoomOnly(on: Boolean) = change { it.copy(roomId = if (on) _state.value.openedIn else null) }

        fun onServer(on: Boolean) = change { it.copy(onServer = on) }

        fun byTime(on: Boolean) = change { it.copy(byTime = on) }

        /** A fresh search whenever what's asked changes. */
        private fun change(edit: (SearchQuery) -> SearchQuery) {
            val query = edit(_state.value.query)
            if (query == _state.value.query && _state.value.hits.isNotEmpty()) return
            running?.cancel()
            _state.update {
                it.copy(query = query, hits = emptyList(), next = null, error = null, loading = query.text.isNotEmpty())
            }
            if (query.text.isNotEmpty()) running = viewModelScope.launch { load(query, null) }
        }

        fun loadMore() {
            val now = _state.value
            val next = now.next ?: return
            if (now.loading) return
            _state.update { it.copy(loading = true) }
            running = viewModelScope.launch { load(now.query, next) }
        }

        private suspend fun load(
            query: SearchQuery,
            next: String?,
        ) {
            search
                .page(query, next)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            hits =
                                (it.hits + page.hits).distinctBy { e ->
                                    e.eventId
                                },
                            next = page.next,
                            loading = false
                        )
                    }
                }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }

        private companion object {
            const val DEBOUNCE_MS = 400L
        }
    }
