package pt.aguiarvieira.xmuks.core.database.outbox

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Update
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * A message the user wrote that gomuks hasn't acknowledged yet. [txnId]/[startTs] are the `/exec`
 * envelope: kept across attempts and restarts so gomuks collapses repeats; replaced only when no
 * attempt can have reached it.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val localId: String,
    val roomId: String,
    /** When the user sent it: the order messages go out in, and the local echo's time. */
    val createdAt: Long,
    val command: String,
    /** The command's JSON parameters (send_message params, a reaction, …). */
    val params: String,
    val txnId: String,
    val startTs: Long,
    /** [OutboxState] name. */
    val state: String,
    /** An attempt may have reached gomuks without an answer: never re-issue under a new txn_id. */
    val maybeDelivered: Boolean = false,
    val error: String? = null,
)

enum class OutboxState {
    /** Waiting to go (or between retries). */
    Queued,

    /** gomuks rejected it (the message itself, not the connection): nothing was sent. */
    Failed,

    /** No answer, and too late to retry safely: it may or may not have been sent. The user decides. */
    Unknown,
}

@Dao
interface OutboxDao {
    @Insert suspend fun insert(entry: OutboxEntity)

    @Update suspend fun update(entry: OutboxEntity)

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun delete(localId: String)

    @Query("SELECT * FROM outbox WHERE localId = :localId")
    suspend fun get(localId: String): OutboxEntity?

    /** Everything still to send, oldest first. */
    @Query("SELECT * FROM outbox WHERE state = 'Queued' ORDER BY createdAt")
    suspend fun queued(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE roomId = :roomId ORDER BY createdAt")
    fun observe(roomId: String): Flow<List<OutboxEntity>>

    @Query("DELETE FROM outbox")
    suspend fun wipe()
}

/**
 * The outbox lives in its own database: the main one is a disposable cache of gomuks (dropped on
 * any schema change), and unsent messages are the one thing that must survive. Schema changes here
 * need real migrations.
 */
@Database(entities = [OutboxEntity::class], version = 1, exportSchema = false)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao

    companion object {
        fun build(
            context: Context,
            name: String? = "outbox.db",
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): OutboxDatabase {
            val builder =
                if (name == null) {
                    Room.inMemoryDatabaseBuilder(context, OutboxDatabase::class.java)
                } else {
                    Room.databaseBuilder(context, OutboxDatabase::class.java, name)
                }
            return builder.setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()
        }
    }
}
