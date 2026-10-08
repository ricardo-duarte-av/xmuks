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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.aguiarvieira.xmuks.core.network.ConnectionState
import pt.aguiarvieira.xmuks.core.network.GomuksConnection

/**
 * Holds `/sse` open only while the app is in the foreground (and logged in). Leaving the
 * foreground keeps it for [lingerMs] so a quick app switch doesn't pay for a reconnect; after that
 * the stream closes and FCM takes over. Network changes (and getting our network back when we
 * return to the foreground) cut any backoff short; a kept stream gone silent is restarted.
 *
 * Coming back, and on a new network, idle pooled connections are dropped first ([dropIdleConnections]).
 * gomuks speaks HTTP/2, so every request shares one connection; one opened in the background (say,
 * by a push) can have died silently since (Android cut our network, a NAT forgot it), and the
 * stream, sending and the room's refresh would all hang on it until a timeout: the room opened
 * from a notification with its progress bar stuck and messages pending for 10–15 s.
 */
class ForegroundConnection(
    private val context: Context,
    private val connection: GomuksConnection,
    private val loggedIn: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val lingerMs: Long = 30_000,
    /** The app came to the foreground. */
    private val onForeground: () -> Unit = {},
    /** Closes pooled connections not in use, so the next requests open fresh ones. */
    private val dropIdleConnections: () -> Unit = {},
) : DefaultLifecycleObserver {
    val state: StateFlow<ConnectionState> = connection.state

    private var running: Job? = null
    private var stopping: Job? = null
    private var inForeground = false

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                // A new network: connections made on the old one are no use.
                override fun onAvailable(network: Network) {
                    dropIdleConnections()
                    connection.reconnectNow()
                }

                // Back in the foreground, Android gives the app its network back: stop waiting.
                override fun onBlockedStatusChanged(
                    network: Network,
                    blocked: Boolean,
                ) {
                    if (!blocked) wakeUp()
                }
            },
        )
        scope.launch { loggedIn.collect { update() } }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Before anything asks gomuks for something: nothing may go out on a connection left from
        // the background.
        dropIdleConnections()
        inForeground = true
        update()
        wakeUp()
        onForeground()
    }

    /** A stream kept through a short background spell may have died silently: start over now if so. */
    private fun wakeUp() {
        if (connection.isStale()) restart() else connection.reconnectNow()
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        update()
    }

    /**
     * In the background (a periodic worker): opens the stream until gomuks has sent everything new
     * (or [timeoutMs] passes), then closes it — unless the app came to the foreground meanwhile,
     * which then keeps it. Nothing to do when it's already running. True if it caught up.
     */
    suspend fun catchUp(timeoutMs: Long): Boolean {
        if (!loggedIn.value) return false
        val job =
            synchronized(this) {
                if (running?.isActive == true) return connection.state.value == ConnectionState.Live
                scope.launch { connection.run() }.also { running = it }
            }
        val live = withTimeoutOrNull(timeoutMs) { connection.state.first { it == ConnectionState.Live } } != null
        synchronized(this) {
            if (!inForeground && running === job) {
                job.cancel()
                running = null
            }
        }
        return live
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
