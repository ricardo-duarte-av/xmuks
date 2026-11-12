package pt.aguiarvieira.xmuks.core.data.sync

import androidx.room3.withWriteTransaction
import pt.aguiarvieira.xmuks.core.database.RoomStateEntity
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.protocol.Event

/**
 * Member events fetched from gomuks' current state (`get_specific_room_state`), written over the
 * ones we hold. A catch-up sync only carries each room's preview sender's member event, so anyone
 * else's profile change made while we weren't streaming never reaches the cache otherwise.
 */
class MemberStore(
    private val db: XmuksDatabase,
) {
    private val dao = db.syncDao()

    suspend fun store(
        roomId: String,
        members: List<Event>,
    ) {
        val fresh = members.filter { it.type == MEMBER && it.roomId == roomId && it.stateKey != null && it.rowId != 0L }
        if (fresh.isEmpty()) return
        db.withWriteTransaction {
            if (!dao.roomExists(roomId)) return@withWriteTransaction
            dao.upsertEvents(fresh.map { it.toEntity() })
            dao.upsertState(fresh.map { RoomStateEntity(roomId, MEMBER, it.stateKey!!, it.rowId) })
            dao.pruneEvents() // the member events they replace
        }
    }

    private companion object {
        const val MEMBER = "m.room.member"
    }
}
