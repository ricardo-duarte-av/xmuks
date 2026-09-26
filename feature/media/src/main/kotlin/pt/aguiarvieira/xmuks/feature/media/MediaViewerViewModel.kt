package pt.aguiarvieira.xmuks.feature.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

/** Owns the player for video and audio, so it survives configuration changes and is released once. */
@HiltViewModel
class MediaViewerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Named("media") private val http: OkHttpClient,
    ) : ViewModel() {
        private var player: ExoPlayer? = null

        /** A player for [url], created on first use; gomuks serves byte ranges, so seeking works. */
        @OptIn(UnstableApi::class)
        fun player(url: String): ExoPlayer =
            player ?: ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    playWhenReady = true
                    prepare()
                }.also { player = it }

        override fun onCleared() {
            player?.release()
            player = null
        }
    }
