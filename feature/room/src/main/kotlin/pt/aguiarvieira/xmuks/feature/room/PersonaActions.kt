package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile

/** Our per-message profiles as this room sees them (its own and the global ones), and ourselves. */
@Immutable
data class Personas(
    val global: PerMessageProfiles = PerMessageProfiles.EMPTY,
    val room: PerMessageProfiles = PerMessageProfiles.EMPTY,
    val me: OwnProfile? = null,
) {
    /** Everything this room can send as: its own profiles first. */
    val choices: List<PerMessageProfile> get() = (room.profiles + global.profiles).distinctBy { it.id }

    /** Who [text] would go out as, by gomuks' rules; null: ourselves. */
    fun active(text: String): PerMessageProfile? = PerMessageProfiles.pick(global, room, text)

    /** The default in force here (triggers aside). */
    val defaultId: String? get() = active("")?.id
}

/** Which per-message profile the room's messages go out as by default. */
class PersonaActions(
    private val scope: CoroutineScope,
    private val roomId: String,
    private val profiles: ProfileRepository,
    me: Flow<OwnProfile?>,
    started: SharingStarted,
) {
    val personas: StateFlow<Personas> =
        combine(profiles.perMessageProfiles, profiles.perMessageProfiles(roomId), me, ::Personas)
            .stateIn(scope, started, Personas())

    /**
     * Makes [id] (null: ourselves) the default. It goes where gomuks will look first: the room's
     * list when the room already has a default or the profile is the room's own, else the global one.
     */
    fun choose(id: String?) {
        val current = personas.value
        scope.launch {
            if (current.room.defaultId != null || current.room.byId(id) != null) {
                profiles.savePerMessageProfiles(current.room.copy(defaultId = id.orEmpty()), roomId)
            } else {
                profiles.savePerMessageProfiles(current.global.copy(defaultId = id))
            }
        }
    }
}
