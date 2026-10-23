package pt.aguiarvieira.xmuks.feature.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaDownloads
import pt.aguiarvieira.xmuks.core.data.media.MediaShares
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.media.PdfFiles
import pt.aguiarvieira.xmuks.core.data.timeline.RoomGallery
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import javax.inject.Inject
import javax.inject.Named

/**
 * Owns the player for video and audio, so it survives configuration changes and is released once;
 * and, for a message's picture or video, the room's others around it to swipe through.
 */
@HiltViewModel
class MediaViewerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Named("player") private val source: DataSource.Factory,
        /** The timeline pictures' cache tier: what the viewer shows is mostly already there. */
        @param:Named("media") val images: ImageLoader,
        val downloads: MediaDownloads,
        val shares: MediaShares,
        private val gallery: RoomGallery,
        private val urls: MediaUrls,
        private val pdf: PdfFiles,
    ) : ViewModel() {
        private var player: ExoPlayer? = null
        private var playing: String? = null

        /** The pictures and videos to swipe through, oldest first; null until (unless) there are some. */
        private val _sequence = MutableStateFlow<List<ViewerMedia>?>(null)
        val sequence: StateFlow<List<ViewerMedia>?> = _sequence.asStateFlow()

        private var opened: ViewerMedia? = null

        /** Where the older media starts; null once the room's start is reached. */
        private var before: Long? = null
        private var loading = false

        /** A player for [url]: one for the viewer, moved to whichever video is shown. */
        @OptIn(UnstableApi::class)
        fun player(url: String): ExoPlayer {
            val existing = player
            if (existing != null) {
                if (playing != url) {
                    existing.setMediaItem(MediaItem.fromUri(url))
                    existing.prepare()
                    existing.playWhenReady = true
                    playing = url
                }
                return existing
            }
            // gomuks serves byte ranges, so seeking works.
            return ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(source))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    playWhenReady = true
                    prepare()
                }.also {
                    player = it
                    playing = url
                }
        }

        /**
         * Finds [media]'s neighbours in its room, once: everything newer, and a first stretch of
         * what's older. Only for a message's picture or video.
         */
        fun open(media: ViewerMedia) {
            if (opened != null) return
            opened = media
            val roomId = media.roomId ?: return
            val eventId = media.eventId ?: return
            if (!media.kind.swipeable) return
            loading = true
            viewModelScope.launch {
                val window = gallery.window(roomId, eventId).getOrNull()
                if (window != null) {
                    before = window.before
                    _sequence.value = merged(window.items.mapNotNull { it.toViewer(roomId, urls) }, media)
                }
                loading = false
            }
        }

        /** The next stretch of older media, as the oldest comes near. */
        fun loadOlder() {
            val media = opened ?: return
            val roomId = media.roomId ?: return
            val from = before ?: return
            if (loading || _sequence.value == null) return
            loading = true
            viewModelScope.launch {
                gallery.page(roomId, from).onSuccess { page ->
                    before = page.before
                    val older = page.items.mapNotNull { it.toViewer(roomId, urls) }.reversed()
                    val known =
                        _sequence.value
                            .orEmpty()
                            .map { it.pageKey }
                            .toSet()
                    _sequence.value = older.filter { it.pageKey !in known } + _sequence.value.orEmpty()
                }
                loading = false
            }
        }

        /** How many pages a PDF has (it's downloaded for that); 0 when it can't be read. */
        suspend fun pageCount(url: String): Int =
            // Unreadable: not downloaded, or a file the platform's renderer won't open (a password).
            runCatching { pdf.pageCount(url) }.getOrElse { e -> if (e is CancellationException) throw e else 0 }

        override fun onCleared() {
            player?.release()
            player = null
        }
    }

/**
 * [newestFirst] oldest first, with the [opened] media standing for its own entry (it carries how
 * it was opened: the element it grows out of); null when there's nothing to swipe to.
 */
internal fun merged(
    newestFirst: List<ViewerMedia>,
    opened: ViewerMedia,
): List<ViewerMedia>? {
    val key = opened.pageKey
    val list = newestFirst.distinctBy { it.pageKey }.reversed().map { if (it.pageKey == key) opened else it }
    // Not among them (a spoiler, opened by a tap): it's shown alone.
    if (list.none { it.pageKey == key }) return null
    return list.takeIf { it.size > 1 }
}
