package pt.aguiarvieira.xmuks.core.data.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.network.GomuksConnection

/**
 * Holds `/sse` open only while the app is in the foreground (and logged in). Leaving the
 * foreground keeps it for [lingerMs] so a quick app switch doesn't pay for a reconnect; after that
 * the stream closes and FCM takes over. Network changes cut any backoff short.
 */
class ForegroundConnection(
    private val context: Context,
    private val connection: GomuksConnection,
    private val loggedIn: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val lingerMs: Long = 30_000,
) : DefaultLifecycleObserver {
    val state: StateFlow<ConnectionState> = connection.state

    private var running: Job? = null
    private var stopping: Job? = null
    private var inForeground = false

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = connection.reconnectNow()
            },
        )
        scope.launch { loggedIn.collect { update() } }
    }

    override fun onStart(owner: LifecycleOwner) {
        inForeground = true
        update()
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        update()
    }

    /** Force a fresh attempt now (e.g. after re-login, or a user tapping "retry"). */
    fun restart() {
        running?.cancel()
        running = null
        update()
    }

    @Synchronized
    private fun update() {
        val wanted = inForeground && loggedIn.value
        if (wanted) {
            stopping?.cancel()
            stopping = null
            if (running?.isActive != true) running = scope.launch { connection.run() }
        } else if (running?.isActive == true && stopping == null) {
            val linger = if (loggedIn.value) lingerMs else 0
            stopping =
                scope.launch {
                    delay(linger)
                    running?.cancel()
                    running = null
                    stopping = null
                }
        }
    }
}
