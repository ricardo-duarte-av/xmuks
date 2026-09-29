package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import java.text.Collator

/** How room list rows look, from gomuks' preferences (`room_list_preview`, `room_list_style`). */
@Immutable
data class RoomListDisplay(
    val showPreview: Boolean = true,
    val avatar: Dp = 48.dp,
    val verticalPadding: Dp = 10.dp,
) {
    companion object {
        fun of(layers: PrefLayers): RoomListDisplay {
            val preview = layers.get(Prefs.roomListPreview)
            return when (layers.get(Prefs.roomListStyle)) {
                "compact" -> RoomListDisplay(preview, avatar = 36.dp, verticalPadding = 6.dp)
                "spacious" -> RoomListDisplay(preview, avatar = 56.dp, verticalPadding = 14.dp)
                else -> RoomListDisplay(preview)
            }
        }
    }
}

val LocalRoomListDisplay = staticCompositionLocalOf { RoomListDisplay() }

/** Names as a reader expects them sorted: by letter, ignoring case and accents, in the device's language. */
private val byName: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Rooms by name instead of recent activity, when `alphabetical_order` says so. */
internal fun Flow<List<RoomSummary>>.ordered(prefs: Flow<PrefLayers>): Flow<List<RoomSummary>> =
    combine(prefs) { rooms, layers ->
        if (layers.get(Prefs.alphabeticalOrder)) rooms.sortedWith(compareBy(byName) { it.name }) else rooms
    }
