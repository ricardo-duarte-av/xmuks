package pt.aguiarvieira.xmuks.feature.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * Audio and video, once played, are kept on disk (least recently played go first), so replaying a
 * voice message or a video doesn't download it again — in the timeline or the viewer: one cache,
 * one instance (media3 allows only one per directory).
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerCacheModule {
    private const val PLAYER_CACHE_BYTES = 256L * 1024 * 1024

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun playerCache(
        @ApplicationContext context: Context,
    ): Cache =
        SimpleCache(
            File(context.cacheDir, "player"),
            LeastRecentlyUsedCacheEvictor(PLAYER_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    /** Reads through the cache from gomuks, on the authenticated media client. */
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    @Named("player")
    fun playerDataSource(
        cache: Cache,
        @Named("media") http: OkHttpClient,
    ): DataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
