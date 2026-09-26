package pt.aguiarvieira.xmuks.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.media.MediaCacheStrategy

@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
class MediaCacheStrategyTest {
    private var now = 100L * MediaCacheStrategy.THIRTY_DAYS_MS
    private val strategy = MediaCacheStrategy(clock = { now })
    private val options = Options(ApplicationProvider.getApplicationContext())
    private val request = NetworkRequest("https://g/_gomuks/media/s/id?thumbnail=avatar")

    private fun cached(ageMs: Long) =
        NetworkResponse(
            code = 200,
            responseMillis = now - ageMs,
            headers =
                NetworkHeaders
                    .Builder()
                    .set("ETag", "\"abc\"")
                    .set("Content-Type", "image/webp")
                    .build(),
        )

    @Test
    fun `within 30 days the disk copy is used without asking`() {
        val cache = cached(ageMs = MediaCacheStrategy.THIRTY_DAYS_MS - 1)
        val result = runBlocking { strategy.read(cache, request, options) }
        assertSame(cache, result.response)
        assertNull(result.request)
    }

    @Test
    fun `after 30 days it revalidates with the ETag`() {
        val result =
            runBlocking { strategy.read(cached(ageMs = MediaCacheStrategy.THIRTY_DAYS_MS + 1), request, options) }
        assertEquals("\"abc\"", result.request!!.headers["If-None-Match"])
    }

    @Test
    fun `a 304 refreshes the entry and keeps the body on disk`() {
        val notModified =
            NetworkResponse(
                code = 304,
                responseMillis = now,
                headers = NetworkHeaders.Builder().set("ETag", "\"abc\"").build()
            )
        val result = runBlocking { strategy.write(cached(ageMs = 1), request, notModified, options) }
        assertEquals(now, result.response!!.responseMillis)
        assertNull("body stays as it is on disk", result.response!!.body)
        assertEquals("image/webp", result.response!!.headers["Content-Type"])
    }

    @Test
    fun `removed media is never cached`() {
        listOf(404, 410, 451, 500).forEach { code ->
            val result =
                runBlocking { strategy.write(cached(ageMs = 1), request, NetworkResponse(code = code), options) }
            assertSame("HTTP $code", CacheStrategy.WriteResult.DISABLED, result)
        }
    }
}
