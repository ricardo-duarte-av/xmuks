package pt.aguiarvieira.xmuks.feature.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pt.aguiarvieira.xmuks.core.call.IncomingCall
import pt.aguiarvieira.xmuks.core.call.IncomingCalls
import pt.aguiarvieira.xmuks.core.call.system.CallService
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import javax.inject.Inject

/**
 * The full-screen ringer, over the lock screen: who's calling, Answer and Decline. It goes away by
 * itself when the ring stops (timed out, the caller gave up, answered on another device).
 */
@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {
    @Inject lateinit var incoming: IncomingCalls

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            XmuksTheme(darkTheme = true) {
                val ringing by incoming.ringing.collectAsStateWithLifecycle()
                val call = ringing
                LaunchedEffect(call) { if (call == null) finish() }
                if (call != null) {
                    IncomingCallScreen(
                        call,
                        onAnswer = { answer(call) },
                        onDecline = { incoming.decline(call.eventId) }
                    )
                }
            }
        }
    }

    private fun answer(call: IncomingCall) {
        incoming.answer(call.roomId)
        runCatching { CallService.openCall(this, call.roomId, video = call.video, answer = true).send() }
        finish()
    }
}

@Composable
internal fun IncomingCallScreen(
    call: IncomingCall,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            RoomAvatar(call.callerName, call.callerId, call.avatarUrl, size = 160.dp)
            Spacer(Modifier.height(24.dp))
            Text(call.callerName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(
                    if (call.video) R.string.call_incoming_video_title else R.string.call_incoming_voice_title
                ) +
                    if (call.isDirect) "" else " · " + call.roomName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingButton(
                    R.drawable.ic_call_end,
                    stringResource(R.string.call_decline),
                    MaterialTheme.colorScheme.error,
                    onDecline
                )
                RingButton(
                    if (call.video) R.drawable.ic_videocam else R.drawable.ic_call,
                    stringResource(R.string.call_answer),
                    MaterialTheme.colorScheme.primary,
                    onAnswer,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RingButton(
    icon: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
            modifier = Modifier.size(80.dp),
        ) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(36.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    }
}
