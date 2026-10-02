package pt.aguiarvieira.xmuks.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WearApplication : Application() {
    @Inject lateinit var account: WatchAccount

    override fun onCreate() {
        super.onCreate()
        account.applyBridging()
        RenewWorker.schedule(this)
    }
}
