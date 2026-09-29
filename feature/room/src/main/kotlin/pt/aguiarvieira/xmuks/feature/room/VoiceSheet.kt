package pt.aguiarvieira.xmuks.feature.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

/**
 * Records a voice message: asks for the microphone the first time, records with a level meter
 * and a timer, then offers to send or discard it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSheet(
    onSend: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recorded by remember { mutableStateOf<File?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var denied by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val start = {
        recording = runCatching { recorder.start() }.isSuccess
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) start() else denied = true
        }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // No half-open step, and no swiping it away mid-sentence by accident.
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.attach_voice), style = MaterialTheme.typography.titleLarge)
            val file = recorded
            when {
                denied -> {
                    Text(
                        stringResource(R.string.voice_no_permission),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                file != null -> {
                    Text(
                        stringResource(R.string.voice_recorded, duration(recordedMs)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            file.delete()
                            onDismiss()
                        }) { Text(stringResource(R.string.voice_discard)) }
                        Button(onClick = {
                            onSend(file)
                            onDismiss()
                        }) { Text(stringResource(R.string.send)) }
                    }
                }

                recording -> {
                    Recording(recorder) {
                        recordedMs = recorder.elapsedMs
                        recorded = recorder.stop()
                        recording = false
                    }
                }
            }
        }
    }
}

/** The timer, a meter of the last few seconds' levels, and Stop. */
@Composable
private fun Recording(
    recorder: VoiceRecorder,
    onStop: () -> Unit,
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = recorder.elapsedMs
            levels += recorder.level()
            if (levels.size > METER_BARS) levels.removeAt(0)
            delay(TICK_MS)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            Text(duration(elapsed), style = MaterialTheme.typography.headlineSmall)
        }
        Row(
            Modifier.height(METER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            levels.forEach { level ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height(METER_HEIGHT * level.coerceIn(MIN_BAR, 1f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                )
            }
        }
        Button(onClick = onStop) { Text(stringResource(R.string.voice_stop)) }
    }
}

private fun duration(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60

private const val TICK_MS = 100L
private const val METER_BARS = 40
private const val MIN_BAR = 0.06f
private val METER_HEIGHT = 48.dp
