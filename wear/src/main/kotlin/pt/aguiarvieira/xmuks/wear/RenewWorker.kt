package pt.aguiarvieira.xmuks.wear

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import java.util.concurrent.TimeUnit

/**
 * gomuks forgets a pusher a week after it was registered, and the watch has no live connection to
 * renew it on (as the phone does): renewed here, daily, while signed in.
 */
class RenewWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun account(): WatchAccount

        fun registrar(): PushRegistrar
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        if (deps.account().signedInAs() != null) deps.registrar().ensureRegistered()
        return Result.success()
    }

    companion object {
        private const val NAME = "renew-push"
        private const val EVERY_HOURS = 12L

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<RenewWorker>(EVERY_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
