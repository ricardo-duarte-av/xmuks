package pt.aguiarvieira.xmuks.feature.room

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pt.aguiarvieira.xmuks.core.data.timeline.RoomMentionPower
import pt.aguiarvieira.xmuks.core.data.timeline.mentionsRoom

/** The draft says @room: whether it will ping everyone ([on]), and whether we may ([allowed], else [needed]). */
data class RoomPingState(
    val on: Boolean,
    val allowed: Boolean = true,
    val needed: Long? = null,
)

/**
 * An @room in the draft pings everyone (`m.mentions.room`) unless turned off, as gomuks web's
 * "Mention @room" box — on for every new message, and only where the room's power levels let us.
 */
class RoomPing(
    scope: CoroutineScope,
    draft: TextFieldState,
    power: Flow<RoomMentionPower?>,
) {
    private val chosen = MutableStateFlow(true)

    /** Until the room's state says otherwise, assumed allowed. */
    private val power = power.stateIn(scope, SharingStarted.Eagerly, null)

    /** Null while the draft doesn't say @room. */
    val state: StateFlow<RoomPingState?> =
        combine(
            snapshotFlow { draft.text.toString() }.map(::mentionsRoom).distinctUntilChanged(),
            chosen,
            this.power,
        ) { said, on, power ->
            if (!said) {
                null
            } else {
                val allowed = power?.allowed != false
                RoomPingState(on && allowed, allowed, power?.needed)
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun toggle() = chosen.update { !it }

    /** Whether [text], being sent, pings everyone; the next message starts with the ping on again. */
    fun take(text: String): Boolean {
        val ping = mentionsRoom(text) && chosen.value && power.value?.allowed != false
        chosen.value = true
        return ping
    }
}

/** The @room switch, and flipping it. */
class RoomPingUi(
    val state: RoomPingState? = null,
    val onToggle: () -> Unit = {},
)

/**
 * Above the message box while the draft says @room: a chip, on by default, that has the message
 * notify everyone here. Without the power to, it stays (dimmed, off) and a tap says what it takes.
 */
@Composable
fun RoomPingChip(
    ping: RoomPingUi,
    modifier: Modifier = Modifier,
) {
    // Kept through the exit animation, once the draft no longer says @room.
    val last = remember { mutableStateOf(ping.state) }
    ping.state?.let { last.value = it }
    AnimatedVisibility(
        visible = ping.state != null,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val state = last.value ?: return@AnimatedVisibility
        val context = LocalContext.current
        val refusal =
            state.needed?.let { stringResource(R.string.room_ping_not_allowed_level, it) }
                ?: stringResource(R.string.room_ping_not_allowed)
        FilterChip(
            selected = state.on,
            onClick = {
                if (state.allowed) ping.onToggle() else Toast.makeText(context, refusal, Toast.LENGTH_LONG).show()
            },
            label = { Text(stringResource(if (state.on) R.string.room_ping_on else R.string.room_ping_off)) },
            leadingIcon = {
                Icon(
                    painterResource(if (state.on) R.drawable.ic_campaign else R.drawable.ic_mention),
                    null,
                    Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            modifier = if (state.allowed) Modifier else Modifier.alpha(DISABLED_ALPHA),
        )
    }
}
