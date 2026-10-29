package pt.aguiarvieira.xmuks.feature.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/**
 * Before joining a call that's already going on: how we'll come in (microphone, camera, where the
 * audio plays), with a look at our camera, and the choice not to go in after all.
 */
@Composable
fun CallLobby(
    roomName: String,
    roomId: String,
    roomAvatarUrl: String?,
    people: Int,
    choice: LobbyChoice,
    onChoice: (LobbyChoice) -> Unit,
    routes: List<AudioRoute>,
    onJoin: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Our camera; [onCamera] asks for the permission first when it isn't granted yet. */
    onCamera: (Boolean) -> Unit = { onChoice(choice.copy(cameraOn = it)) },
    preview: @Composable (Modifier) -> Unit = { CameraPreview(it) },
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoomAvatar(roomName, roomId, roomAvatarUrl, size = 48.dp)
                Column {
                    Text(
                        roomName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.call_lobby_people, people, people),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (choice.cameraOn) {
                    preview(Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.ic_videocam_off),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.call_lobby_camera_off),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                FilledTonalIconToggleButton(
                    checked = !choice.microphoneOn,
                    onCheckedChange = { onChoice(choice.copy(microphoneOn = !choice.microphoneOn)) },
                    modifier = Modifier.size(LOBBY_CONTROL),
                ) {
                    Icon(
                        painterResource(if (choice.microphoneOn) R.drawable.ic_mic else R.drawable.ic_mic_off),
                        contentDescription =
                            stringResource(if (choice.microphoneOn) R.string.call_mute else R.string.call_unmute),
                    )
                }
                FilledTonalIconToggleButton(
                    checked = choice.cameraOn,
                    onCheckedChange = onCamera,
                    modifier = Modifier.size(LOBBY_CONTROL),
                ) {
                    Icon(
                        painterResource(if (choice.cameraOn) R.drawable.ic_videocam else R.drawable.ic_videocam_off),
                        contentDescription =
                            stringResource(if (choice.cameraOn) R.string.call_camera_off else R.string.call_camera_on),
                    )
                }
                if (routes.size > 1) StartingRouteButton(routes, choice.route) { onChoice(choice.copy(route = it)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(LOBBY_BUTTON)) {
                    Text(stringResource(R.string.call_lobby_cancel))
                }
                Button(onClick = onJoin, modifier = Modifier.weight(1f).height(LOBBY_BUTTON)) {
                    Icon(painterResource(R.drawable.ic_call), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.call_lobby_join))
                }
            }
        }
    }
}

/** The lobby, and the camera permission when the camera is turned on in it. */
@Composable
internal fun LobbyRoute(
    roomId: String,
    ui: CallUi,
    people: Int,
    routes: List<AudioRoute>,
    choice: LobbyChoice,
    onChoice: (LobbyChoice) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(choice)
    // Turning the camera on in the lobby of an audio call: it may not be allowed yet.
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onChoice(latest.copy(cameraOn = true))
        }
    BackHandler(onBack = onBack)
    CallLobby(
        roomName = ui.roomName,
        roomId = roomId,
        roomAvatarUrl = ui.roomAvatarUrl,
        people = people,
        choice = choice,
        onChoice = onChoice,
        routes = routes,
        onJoin = onJoin,
        onCancel = onBack,
        onCamera = { on ->
            val allowed =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            if (!on ||
                allowed
            ) {
                onChoice(choice.copy(cameraOn = on))
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        },
        modifier = modifier,
    )
}

/** Our front camera, before there's a call to send it to; let go as soon as the lobby goes. */
@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val camera = remember { LobbyCamera(context) }
    DisposableEffect(camera) { onDispose { camera.release() } }
    VideoView(camera.track, camera.room, mirror = true, modifier = modifier)
}

private class LobbyCamera(
    context: Context,
) {
    val room: Room = LiveKit.create(context.applicationContext)
    val track: LocalVideoTrack = room.localParticipant.createVideoTrack().also { it.startCapture() }

    fun release() {
        track.stopCapture()
        track.dispose()
        room.release()
    }
}

internal val LOBBY_CONTROL = 64.dp
private val LOBBY_BUTTON = 56.dp
