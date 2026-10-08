package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import pt.aguiarvieira.xmuks.core.call.CallManager
import pt.aguiarvieira.xmuks.core.call.CallPhase
import javax.inject.Inject

/** The call we're in, as the strip over every screen shows it. */
@Immutable
data class OngoingCall(
    val roomId: String,
    val roomName: String,
    val microphoneOn: Boolean,
    val video: Boolean,
    val connectedAt: Long?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OngoingCallViewModel
    @Inject
    constructor(
        private val manager: CallManager,
    ) : ViewModel() {
        val ongoing: StateFlow<OngoingCall?> =
            manager.active
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(null)
                    } else {
                        combine(session.phase, session.microphoneOn, session.cameraOn) { phase, mic, cam ->
                            if (phase is CallPhase.Ended) {
                                null
                            } else {
                                OngoingCall(session.room.roomId, session.room.name, mic, cam, session.connectedAt)
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        fun toggleMicrophone() {
            val session = manager.active.value ?: return
            session.setMicrophone(!session.microphoneOn.value)
        }

        fun hangUp() = manager.hangUp()

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

/**
 * A slim bar over the app while we're in a call but not looking at it: the room, how long, mute and
 * hang up; tapping it goes back to the call. Hidden while [hidden] (the call screen is showing).
 */
@Composable
fun OngoingCallStrip(
    hidden: (roomId: String) -> Boolean,
    onOpen: (roomId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OngoingCallViewModel = hiltViewModel(),
) {
    val ongoing by viewModel.ongoing.collectAsStateWithLifecycle()
    val call = ongoing
    AnimatedVisibility(
        visible = call != null && !hidden(call.roomId),
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        if (call != null) {
            OngoingCallBar(call, { onOpen(call.roomId) }, viewModel::toggleMicrophone, viewModel::hangUp)
        }
    }
}

@Composable
internal fun OngoingCallBar(
    call: OngoingCall,
    onOpen: () -> Unit,
    onMicrophone: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.statusBarsPadding().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (call.video) R.drawable.ic_videocam else R.drawable.ic_call),
                null,
                Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    call.roomName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    call.connectedAt?.let { formatDuration(now - it) } ?: stringResource(R.string.call_connecting),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onMicrophone) {
                Icon(
                    painterResource(if (call.microphoneOn) R.drawable.ic_mic else R.drawable.ic_mic_off),
                    contentDescription =
                        stringResource(
                            if (call.microphoneOn) R.string.call_mute else R.string.call_unmute
                        ),
                )
            }
            IconButton(onClick = onHangUp) {
                Icon(
                    painterResource(R.drawable.ic_call_end),
                    contentDescription = stringResource(R.string.call_hang_up),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val TICK_MS = 1_000L
