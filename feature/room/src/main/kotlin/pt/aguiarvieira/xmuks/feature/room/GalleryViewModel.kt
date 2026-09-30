package pt.aguiarvieira.xmuks.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.timeline.GalleryItem
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.RoomGallery
import javax.inject.Named

/** Which media the gallery shows. */
enum class GalleryFilter { All, Visual, Audio, Files }

/** The gallery so far: its items newest first, whether more are coming, and why not if it failed. */
data class GalleryState(
    val items: List<GalleryItem> = emptyList(),
    val filter: GalleryFilter = GalleryFilter.All,
    val loading: Boolean = true,
    val done: Boolean = false,
    val error: String? = null,
) {
    val shown: List<GalleryItem>
        get() = items.filter { filter.admits(it.content) }
}

internal fun GalleryFilter.admits(content: MessageContent): Boolean =
    when (this) {
        GalleryFilter.All -> true
        GalleryFilter.Visual -> content is MessageContent.Image || content is MessageContent.Video
        GalleryFilter.Audio -> content is MessageContent.Audio
        GalleryFilter.Files -> content is MessageContent.File
    }

@HiltViewModel(assistedFactory = GalleryViewModel.Factory::class)
class GalleryViewModel
    @AssistedInject
    constructor(
        @Assisted private val roomId: String,
        private val gallery: RoomGallery,
        val media: MediaUrls,
        /** Timeline pictures' own cache tier: the gallery's thumbnails are the same ones. */
        @Named("media") val mediaImages: ImageLoader,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): GalleryViewModel
        }

        private val _state = MutableStateFlow(GalleryState())
        val state: StateFlow<GalleryState> = _state.asStateFlow()

        /** Where the next (older) page starts; 0 before the first. */
        private var before = 0L
        private var running = false

        init {
            loadMore()
        }

        fun filter(filter: GalleryFilter) = _state.update { it.copy(filter = filter) }

        /**
         * The next pages, until they bring at least [WANTED] items of what's shown (a stretch of plain
         * chat has none) or the room's start is reached.
         */
        fun loadMore() {
            if (running || _state.value.done) return
            running = true
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                var found = 0
                var pages = 0
                while (found < WANTED && pages < MAX_PAGES) {
                    val page =
                        gallery.page(roomId, before).getOrElse { e ->
                            _state.update { it.copy(loading = false, error = e.message) }
                            running = false
                            return@launch
                        }
                    pages++
                    found += page.items.count { _state.value.filter.admits(it.content) }
                    _state.update { it.copy(items = it.items + page.items) }
                    val next = page.before
                    if (next == null) {
                        _state.update { it.copy(done = true) }
                        break
                    }
                    before = next
                }
                _state.update { it.copy(loading = false) }
                running = false
            }
        }

        private companion object {
            const val WANTED = 24
            const val MAX_PAGES = 10
        }
    }
