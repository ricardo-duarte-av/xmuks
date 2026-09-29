package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the inline player has loaded: which message ([key]), and where it is. */
@Immutable
data class Playback(
    val key: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val buffering: Boolean,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Plays voice messages, audio and videos inside their bubbles: one player for the whole room, so
 * starting one stops the other. Media comes through gomuks (decrypted there) on the authenticated
 * client. Made on first use, released with the room.
 */
class InlinePlayer(
    private val context: Context,
    /** gomuks through the player cache: played once, replayed from disk. */
    private val source: DataSource.Factory,
    private val scope: CoroutineScope,
) {
    private var exo: ExoPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow<Playback?>(null)
    val state: StateFlow<Playback?> = _state.asStateFlow()

    /** The player, for a video surface to show; only the item it's playing should attach it. */
    val player: Player? get() = exo

    /** Starts [key] (from [url]), or pauses/resumes it when it's the one loaded. */
    fun toggle(
        key: String,
        url: String,
    ) {
        val player = exo ?: create().also { exo = it }
        if (_state.value?.key == key) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        _state.value = Playback(key, playing = true, positionMs = 0, durationMs = 0, buffering = true)
        track()
    }

    /** Stops [key] if it's what's loaded (it scrolled away, or went fullscreen). */
    fun pause(key: String) {
        if (_state.value?.key == key) exo?.pause()
    }

    @OptIn(UnstableApi::class) // the media source factory, as the media viewer uses it
    private fun create(): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            // Take the audio focus like any player (and give it back): no talking over music.
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            ).setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(
                    object : Player.Listener {
                        override fun onEvents(
                            player: Player,
                            events: Player.Events,
                        ) = publish()

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            // Finished: back to the start, ready to play again.
                            if (playbackState == Player.STATE_ENDED) {
                                pause()
                                seekTo(0)
                            }
                        }
                    },
                )
            }

    /** Position updates while something plays (the player itself only reports changes). */
    private fun track() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive) {
                    publish()
                    delay(TICK_MS)
                }
            }
    }

    private fun publish() {
        val player = exo ?: return
        val current = _state.value ?: return
        _state.value =
            current.copy(
                playing = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: current.durationMs,
                buffering = player.playbackState == Player.STATE_BUFFERING,
            )
    }

    fun release() {
        ticker?.cancel()
        exo?.release()
        exo = null
        _state.value = null
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
