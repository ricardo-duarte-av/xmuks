package pt.aguiarvieira.xmuks.core.data.rooms

/** Puts a room on the home screen (implemented where notifications build their room shortcuts). */
fun interface RoomShortcuts {
    /** Asks the launcher to pin [room]; false when it can't (no support, or it refused). */
    suspend fun pin(room: RoomSummary): Boolean
}
