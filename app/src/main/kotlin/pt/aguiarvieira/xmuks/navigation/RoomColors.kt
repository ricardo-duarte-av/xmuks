package pt.aguiarvieira.xmuks.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.designsystem.theme.RoomTheme
import pt.aguiarvieira.xmuks.core.designsystem.theme.rememberAvatarSeed
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A room's screens (the room and its threads, info, members, state, gallery, its preferences,
 * searching it, people seen from it) in the room's colours, from its avatar, unless turned off
 * (Appearance, globally or for the room). Outside a room ([roomId] null), the app's colours.
 */
@Composable
internal fun RoomColors(
    roomId: String?,
    colors: RoomColorsViewModel = hiltViewModel(key = "room-colors"),
    content: @Composable () -> Unit,
) {
    if (roomId == null) {
        content()
        return
    }
    val avatarUrl by remember(roomId) { colors.avatarUrl(roomId) }
        .collectAsStateWithLifecycle(initialValue = RoomColorsViewModel.lastKnown(roomId))
    RoomTheme(rememberAvatarSeed(avatarUrl), content)
}

@HiltViewModel
internal class RoomColorsViewModel
    @Inject
    constructor(
        private val rooms: RoomListRepository,
        private val preferences: PreferenceStore,
    ) : ViewModel() {
        /** The avatar a room's colours come from: null with none, or with them turned off. */
        fun avatarUrl(roomId: String): Flow<String?> =
            combine(rooms.room(roomId), preferences.value(Prefs.roomAvatarColors, roomId)) { room, on ->
                room?.avatarUrl?.takeIf { on }
            }.onEach { lastUrls[roomId] = it ?: NONE }

        companion object {
            /**
             * Each room's last answer, so its next screen starts in its colours instead of the
             * app's and fading over a frame later (the database answers just after the first frame).
             */
            private val lastUrls = ConcurrentHashMap<String, String>()
            private const val NONE = ""

            fun lastKnown(roomId: String): String? = lastUrls[roomId]?.takeIf { it != NONE }
        }
    }
