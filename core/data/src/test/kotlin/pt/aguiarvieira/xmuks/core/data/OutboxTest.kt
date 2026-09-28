package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxDatabase
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxEntity
import pt.aguiarvieira.xmuks.core.database.outbox.OutboxState
import pt.aguiarvieira.xmuks.core.network.ExecResult
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * The outbox's one promise: a message is never sent twice. Every scenario here checks which
 * `txn_id`s reached the transport — gomuks collapses repeats of one txn_id, so "twice" means two
 * different txn_ids for the same message that could both have reached gomuks.
 */
@RunWith(AndroidJUnit4::class)
class OutboxTest {
    private val db =
        OutboxDatabase.build(ApplicationProvider.getApplicationContext(), name = null, driver = AndroidSQLiteDriver())
    private val dao = db.outboxDao()
    private var now = 1_000_000L
    private var ids = 0

    private data class Call(
        val room: String?,
        val txnId: String,
        val startTs: Long,
    )

    private val calls = mutableListOf<Call>()
    private val echoes = mutableListOf<Event>()
    private var answers = ArrayDeque<ExecResult>()

    private val outbox =
        Outbox(
            dao = dao,
            transport = { _: String, data: JsonElement, txnId: String, startTs: Long ->
                calls += Call((data.jsonObject["room_id"] as? JsonPrimitive)?.content, txnId, startTs)
                answers.removeFirstOrNull() ?: accepted()
            },
            onAccepted = { echoes += it },
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { now },
            newId = { "id${ids++}" },
        )

    @After fun close() = db.close()

    private fun accepted(rowId: Long = 7) =
        ExecResult.Ok(
            GomuksJson.parseToJsonElement(
                """{"rowid":$rowId,"room_id":"!r","event_id":"","sender":"@me:x","type":"m.room.message","pending":true}""",
            ),
        )

    private fun reachedButNoAnswer() = ExecResult.NetworkError(SocketTimeoutException("read timed out"))

    private fun neverConnected() = ExecResult.NetworkError(ConnectException("refused"))

    private fun send(room: String = "!r") =
        runBlocking {
            outbox.sendMessage(
                room,
                buildJsonObject {
                    put("room_id", JsonPrimitive(room))
                    put("text", JsonPrimitive("hi"))
                },
            )
        }

    private fun pass() = runBlocking { outbox.sendPass() }

    private fun all(room: String = "!r"): List<OutboxEntity> = runBlocking { dao.observe(room).first() }

    @Test
    fun `accepted on the first try leaves the outbox and becomes gomuks' local echo`() {
        send()
        pass()
        assertEquals(1, calls.size)
        assertTrue(all().isEmpty())
        assertEquals(listOf(7L), echoes.map { it.rowId })
    }

    @Test
    fun `retries within the window reuse the same txn_id`() {
        send()
        answers = ArrayDeque(listOf(reachedButNoAnswer(), reachedButNoAnswer()))
        pass()
        now += 10_000
        pass()
        now += 10_000
        pass()
        assertEquals(3, calls.size)
        assertEquals(1, calls.map { it.txnId }.distinct().size)
        assertTrue(all().isEmpty())
    }

    @Test
    fun `no answer and the window gone - it may have been sent, so it is never sent again`() {
        send()
        answers = ArrayDeque(listOf(reachedButNoAnswer()))
        pass()
        now += Outbox.SAFE_WINDOW_MS + 1
        pass()
        pass()
        assertEquals(1, calls.size)
        assertEquals(OutboxState.Unknown.name, all().single().state)
    }

    @Test
    fun `if nothing ever reached gomuks, a late retry gets a fresh envelope`() {
        send()
        answers = ArrayDeque(listOf(neverConnected()))
        pass()
        now += Outbox.SAFE_WINDOW_MS + 1
        pass()
        assertEquals(2, calls.size)
        assertEquals(2, calls.map { it.txnId }.distinct().size) // safe: the first never arrived
        assertTrue(calls[1].startTs > calls[0].startTs)
        assertTrue(all().isEmpty())
    }

    @Test
    fun `a crash mid-request counts as maybe sent`() {
        // What the outbox writes just before a request leaves, left behind by a killed process.
        runBlocking {
            dao.insert(
                OutboxEntity(
                    localId = "crashed",
                    roomId = "!r",
                    createdAt = now,
                    command = Outbox.SEND_MESSAGE,
                    params = "{}",
                    txnId = "t1",
                    startTs = now,
                    state = OutboxState.Queued.name,
                    maybeDelivered = true,
                ),
            )
        }
        now += Outbox.SAFE_WINDOW_MS + 1
        pass()
        assertTrue(calls.isEmpty())
        assertEquals(OutboxState.Unknown.name, all().single().state)
    }

    @Test
    fun `gomuks rejecting the message fails it without retrying`() {
        send()
        answers = ArrayDeque(listOf(ExecResult.CommandError(418, "FI.MAU.GOMUKS.COMMAND_ERROR", "unknown command")))
        pass()
        pass()
        assertEquals(1, calls.size)
        val entry = all().single()
        assertEquals(OutboxState.Failed.name, entry.state)
        assertEquals("unknown command", entry.error)
    }

    @Test
    fun `an expired envelope after a possible delivery is unknown, not resent`() {
        send()
        answers =
            ArrayDeque(
                listOf(
                    reachedButNoAnswer(),
                    ExecResult.CommandError(400, "FI.MAU.GOMUKS.REQUEST_EXPIRED", "expired"),
                ),
            )
        pass()
        pass()
        pass()
        assertEquals(2, calls.size)
        assertEquals(1, calls.map { it.txnId }.distinct().size)
        assertEquals(OutboxState.Unknown.name, all().single().state)
    }

    @Test
    fun `resend and discard are the user's choice`() {
        send()
        answers = ArrayDeque(listOf(reachedButNoAnswer()))
        pass()
        now += Outbox.SAFE_WINDOW_MS + 1
        pass()
        val id = all().single().localId
        runBlocking { outbox.resend(id) }
        pass()
        assertEquals(2, calls.map { it.txnId }.distinct().size) // the user chose to risk it
        assertTrue(all().isEmpty())

        send()
        answers = ArrayDeque(listOf(ExecResult.CommandError(418, null, "nope")))
        pass()
        runBlocking { outbox.discard(all().single().localId) }
        assertTrue(all().isEmpty())
    }

    @Test
    fun `a stuck room doesn't hold up another, and order holds within a room`() {
        send("!stuck")
        send("!ok")
        send("!ok")
        answers = ArrayDeque(listOf(reachedButNoAnswer(), accepted(1)))
        pass() // !stuck: no answer; first !ok: accepted
        pass() // !stuck: accepted on retry; second !ok: accepted
        assertTrue(all("!ok").isEmpty())
        assertTrue(all("!stuck").isEmpty())
        // ids: each send takes one for its localId, then one for its txn_id.
        assertEquals(listOf("xmuks-id3", "xmuks-id5"), calls.filter { it.room == "!ok" }.map { it.txnId })
        assertEquals(listOf("xmuks-id1", "xmuks-id1"), calls.filter { it.room == "!stuck" }.map { it.txnId })
    }

    @Test
    fun `not logged in never reaches anything`() {
        send()
        answers = ArrayDeque(listOf(ExecResult.NetworkError(IOException("Not logged in"))))
        pass()
        now += Outbox.SAFE_WINDOW_MS + 1
        pass()
        assertEquals(2, calls.map { it.txnId }.distinct().size)
        assertTrue(all().isEmpty())
    }
}
