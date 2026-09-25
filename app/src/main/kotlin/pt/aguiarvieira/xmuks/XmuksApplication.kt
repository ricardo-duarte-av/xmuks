package pt.aguiarvieira.xmuks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import javax.inject.Inject

@HiltAndroidApp
class XmuksApplication : Application() {
    @Inject lateinit var connection: ForegroundConnection

    override fun onCreate() {
        super.onCreate()
        connection.install()
    }
}
