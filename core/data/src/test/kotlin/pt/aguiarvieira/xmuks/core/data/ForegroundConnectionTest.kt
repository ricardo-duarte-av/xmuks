package pt.aguiarvieira.xmuks.core.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.network.GomuksConnection
import pt.aguiarvieira.xmuks.core.network.ResumePoint
import pt.aguiarvieira.xmuks.core.network.ResumeStore
import pt.aguiarvieira.xmuks.core.network.SseClient

@RunWith(RobolectricTestRunner::class)
class ForegroundConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val calls = mutableListOf<String>()
    private val http = OkHttpClient()
    private val connection =
        GomuksConnection(
            SseClient(http, { null }, Dispatchers.Unconfined),
            http,
            { null },
            object : ResumeStore {
                override suspend fun load(reconnect: Boolean) = ResumePoint()

                override suspend fun save(point: ResumePoint) = Unit
            },
            Dispatchers.Unconfined,
        ) {}
    private val foreground =
        ForegroundConnection(
            ApplicationProvider.getApplicationContext(),
            connection,
            loggedIn = MutableStateFlow(false),
            scope = scope,
            onForeground = { calls += "foreground" },
            dropIdleConnections = { calls += "drop idle connections" },
        )
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = error("not used")
        }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `back in the foreground, connections from the background go before anything is asked`() {
        foreground.onStart(owner)
        assertEquals(listOf("drop idle connections", "foreground"), calls)
    }

    @Test
    fun `leaving the foreground keeps them`() {
        foreground.onStop(owner)
        assertEquals(emptyList<String>(), calls)
    }
}
