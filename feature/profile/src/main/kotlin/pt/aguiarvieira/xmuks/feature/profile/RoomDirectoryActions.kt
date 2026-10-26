package pt.aguiarvieira.xmuks.feature.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.roominfo.DirectoryUnsupported
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository

/** Whether a room is listed in our server's directory, as far as we know. */
data class DirectoryListing(
    /** Our server's name; empty until known. */
    val server: String = "",
    /** Null until known, or if it can't be. */
    val listed: Boolean? = null,
    /** gomuks has the directory's commands. */
    val supported: Boolean = true,
    /** Not a DM: DMs aren't listed. */
    val applies: Boolean = false,
)

/** Whether this room is listed in our server's directory, and changing that (through [tasks]). */
class RoomDirectoryActions(
    private val scope: CoroutineScope,
    private val roomId: String,
    private val directory: RoomDirectoryRepository,
    private val tasks: ProfileTasks,
    me: Flow<String?>,
    isDirect: Flow<Boolean?>,
    started: SharingStarted,
) {
    private val listed = MutableStateFlow<Result<Boolean>?>(null)

    val listing: StateFlow<DirectoryListing> =
        combine(me, isDirect, listed) { me, direct, listed ->
            DirectoryListing(
                server = me?.substringAfter(':').orEmpty(),
                listed = listed?.getOrNull(),
                supported = listed?.exceptionOrNull() !is DirectoryUnsupported,
                applies = direct != true,
            )
        }.stateIn(scope, started, DirectoryListing())

    init {
        scope.launch { load() }
    }

    /** Lists the room in our server's directory, or takes it off. */
    fun set(listed: Boolean) = tasks.run({ directory.setListed(roomId, listed) }, then = ::load)

    private suspend fun load() {
        listed.value = directory.listed(roomId)
    }
}
