package pt.aguiarvieira.xmuks.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        RoomEntity::class, SpaceEdgeEntity::class, TopLevelSpaceEntity::class, EventEntity::class,
        RoomStateEntity::class, TimelineEntity::class, ReceiptEntity::class, AccountDataEntity::class,
        InvitedRoomEntity::class, SyncMetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class XmuksDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao

    abstract fun roomListDao(): RoomListDao

    companion object {
        /**
         * The database is a cache of gomuks: on any schema change it is dropped and rebuilt by a full
         * sync (its sync_meta goes with it, so the next connection asks for one) — no migrations.
         */
        fun build(
            context: Context,
            name: String? = "xmuks.db",
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): XmuksDatabase {
            val builder =
                if (name == null) {
                    Room.inMemoryDatabaseBuilder(context, XmuksDatabase::class.java)
                } else {
                    Room.databaseBuilder(context, XmuksDatabase::class.java, name)
                }
            return builder
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
