package pt.aguiarvieira.xmuks

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import javax.inject.Inject

@HiltAndroidApp
class XmuksApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var connection: ForegroundConnection

    @Inject lateinit var imageLoader: ImageLoader

    /** Every Coil image in the app loads through gomuks' authenticated client. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        connection.install()
    }
}
