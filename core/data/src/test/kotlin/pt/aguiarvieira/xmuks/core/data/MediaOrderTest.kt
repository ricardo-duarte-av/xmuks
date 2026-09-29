package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.MediaUploader
import pt.aguiarvieira.xmuks.core.data.media.PreparedMedia
import pt.aguiarvieira.xmuks.core.data.media.UploadSource
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** Files sent together reach the room in the order they were picked, whichever uploads first. */
@RunWith(AndroidJUnit4::class)
class MediaOrderTest {
    private val db =
        OutboxDatabase.build(ApplicationProvider.getApplicationContext(), name = null, driver = AndroidSQLiteDriver())

    @After fun close() = db.close()

    /** A gomuks that takes its time over the file called "slow": progress lines, then the content. */
    private val http =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val name =
                    chain
                        .request()
                        .url
                        .queryParameter("filename")
                        .orEmpty()
                if (name == "slow") Thread.sleep(SLOW_MS)
                val body = "0.5\n1\n{\"url\":\"mxc://s/$name\"}\n"
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }.build()

    private fun media(name: String) = PreparedMedia(UploadSource(3) { "abc".byteInputStream() }, name, "application/octet-stream", MediaKind.File, null, null, null)

    @Test
    fun `a quick upload waits for the slower one picked before it`() =
        runBlocking {
            val sentFiles = mutableListOf<String>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val outbox =
                Outbox(
                    dao = db.outboxDao(),
                    transport = { _: String, data: JsonElement, _: String, _: Long ->
                        val content = data.jsonObject["base_content"] as? JsonObject
                        synchronized(sentFiles) { sentFiles += (content?.get("url") as? JsonPrimitive)?.content.orEmpty().substringAfterLast("/") }
                        ExecResult.Ok(
                            GomuksJson.parseToJsonElement(
                                """{"rowid":1,"room_id":"!r","event_id":"","sender":"@me:x","type":"m.room.message","pending":true}""",
                            ),
                        )
                    },
                    onAccepted = {},
                    scope = scope,
                )
            val sender = MediaSender(MediaUploader(http, { "http://gomuks.test".toHttpUrl() }, Dispatchers.IO), outbox, scope)
            sender.send("!r", media("slow"), "", null, encrypt = false)
            sender.send("!r", media("quick"), "", null, encrypt = false)
            val done =
                kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS) {
                    while (synchronized(sentFiles) { sentFiles.size } < 2) {
                        outbox.sendPass()
                        delay(POLL_MS)
                    }
                }
            if (done == null) error("stuck: sent=$sentFiles uploads=${sender.observe("!r").let { kotlinx.coroutines.runBlocking { it.first() } }}")
            assertEquals(listOf("slow", "quick"), sentFiles)
        }

    private companion object {
        const val SLOW_MS = 400L
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
