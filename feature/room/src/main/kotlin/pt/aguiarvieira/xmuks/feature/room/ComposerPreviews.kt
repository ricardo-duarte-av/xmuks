package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.LinkPreviewFetcher
import pt.aguiarvieira.xmuks.core.data.timeline.BundledPreview
import pt.aguiarvieira.xmuks.core.data.timeline.previewableLinks

/** A link in the draft and where its preview stands: offered, loading, ready, or failed. */
data class ComposerPreview(
    val url: String,
    val loading: Boolean = false,
    val ready: BundledPreview? = null,
    val failed: Boolean = false,
)

/**
 * Link previews for the message being written, like gomuks web: each link in the draft is
 * offered, fetched only when asked (the homeserver fetches the page), and bundled when sent.
 */
internal class ComposerPreviews(
    private val scope: CoroutineScope,
    draft: TextFieldState,
    /** Whether previews are offered at all here (gomuks' `send_bundled_url_previews`, and not editing). */
    enabled: Flow<Boolean>,
    private val encrypted: () -> Boolean,
    private val fetcher: LinkPreviewFetcher,
) {
    private val fetched = MutableStateFlow<Map<String, ComposerPreview>>(emptyMap())
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val links: Flow<List<String>> =
        snapshotFlow { draft.text.toString() }
            .debounce(DEBOUNCE_MS)
            .map(::previewableLinks)
            .distinctUntilChanged()

    val previews: StateFlow<List<ComposerPreview>> =
        combine(links, enabled, fetched, dismissed) { links, on, fetched, dismissed ->
            if (!on) emptyList() else links.filter { it !in dismissed }.map { fetched[it] ?: ComposerPreview(it) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun load(url: String) {
        if (fetched.value[url]?.let { it.loading || it.ready != null } == true) return
        fetched.update { it + (url to ComposerPreview(url, loading = true)) }
        scope.launch {
            val result = fetcher.fetch(url, encrypted())
            fetched.update {
                it + (url to ComposerPreview(url, ready = result.getOrNull(), failed = result.isFailure))
            }
        }
    }

    fun dismiss(url: String) = dismissed.update { it + url }

    /** What goes with the message being sent; the slate is wiped for the next one. */
    fun take(): List<BundledPreview> {
        val ready = previews.value.mapNotNull { it.ready }
        fetched.value = emptyMap()
        dismissed.value = emptySet()
        return ready
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
