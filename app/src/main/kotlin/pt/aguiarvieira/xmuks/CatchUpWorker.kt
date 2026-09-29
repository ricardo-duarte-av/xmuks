package pt.aguiarvieira.xmuks

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
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.push.PushRegistrar
import java.util.concurrent.TimeUnit

/**
 * Every few hours, while the app isn't open: renews the push registration before it expires (it
 * lasts a week, and would otherwise only be renewed by opening the app), and catches up with
 * gomuks so the next open starts from fresh rooms instead of a long catch-up.
 */
class CatchUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun push(): PushRegistrar

        fun connection(): ForegroundConnection
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        runCatching { deps.push().ensureRegistered() }
        val caughtUp = deps.connection().catchUp(CATCH_UP_TIMEOUT_MS)
        // Unreachable gomuks: try again sooner than the next period.
        return if (caughtUp) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "catch-up"
        private const val PERIOD_HOURS = 6L
        private const val CATCH_UP_TIMEOUT_MS = 90_000L

        /** Once per install; kept as scheduled across launches. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<CatchUpWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            // Not set up where app startup initializers don't run (Robolectric): nothing to schedule.
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
