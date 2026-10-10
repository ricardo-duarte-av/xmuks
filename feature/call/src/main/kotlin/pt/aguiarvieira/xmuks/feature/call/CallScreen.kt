package pt.aguiarvieira.xmuks.feature.call

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pt.aguiarvieira.xmuks.core.call.CallPhase
import pt.aguiarvieira.xmuks.core.call.CallReaction
import pt.aguiarvieira.xmuks.core.call.EndReason
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.call.system.RouteKind
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/**
 * The call in [roomId]: joins it on arrival (after asking for the microphone, and the camera for a
 * video call), and leaves the screen — not the call — on back.
 */
@Composable
fun CallRoute(
    roomId: String,
    video: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    answer: Boolean = false,
    viewModel: CallViewModel =
        hiltViewModel<CallViewModel, CallViewModel.Factory>(key = "call:$roomId") { it.create(roomId) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) {
                viewModel.join(video && granted[Manifest.permission.CAMERA] == true, answer)
            } else {
                onBack()
            }
        }
    LaunchedEffect(Unit) {
        if (asked || ui.phase != null) return@LaunchedEffect
        asked = true
        val wanted = listOfNotNull(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA.takeIf { video })
        val missing =
            wanted.filter {
                ContextCompat.checkSelfPermission(context, it) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) viewModel.join(video, answer) else permissions.launch(missing.toTypedArray())
    }
    // Close once the call this screen showed is over: ended, or already forgotten by the manager.
    val leave by rememberUpdatedState(onBack)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ui.phase) {
        when (val phase = ui.phase) {
            null -> {
                if (shown) leave()
            }

            is CallPhase.Ended -> {
                endedMessage(phase)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                leave()
            }

            else -> {
                shown = true
            }
        }
    }
    BackHandler(onBack = onBack)
    if (ui.phase == CallPhase.Connected) FullScreenRingPrompt()
    EarProximity(ui, onRoute = viewModel::selectRoute, onCamera = viewModel::setCamera)
    CallScreen(
        // Leaving after the call is forgotten: keep saying it ended, not "Connecting…", while the screen goes.
        ui = if (ui.phase == null && shown) ui.copy(phase = CallPhase.Ended()) else ui,
        onMinimise = onBack,
        onMicrophone = viewModel::setMicrophone,
        onCamera = viewModel::setCamera,
        onFlipCamera = viewModel::flipCamera,
        onHangUp = viewModel::hangUp,
        onRoute = viewModel::selectRoute,
        onHand = viewModel::raiseHand,
        onReact = viewModel::react,
        modifier = modifier.awakeDuring(ui.phase),
    )
}

/** Joining, in or rejoining the call: not before it, nor once it's over. */
internal val CallPhase?.live: Boolean get() = this != null && this !is CallPhase.Ended

/** In the call, the screen stays on (but for [EarProximity]); it may sleep again once the call is over. */
private fun Modifier.awakeDuring(phase: CallPhase?): Modifier = if (phase.live) keepScreenOn() else this

@Composable
fun CallScreen(
    ui: CallUi,
    onMinimise: () -> Unit,
    onMicrophone: (Boolean) -> Unit,
    onCamera: (Boolean) -> Unit,
    onFlipCamera: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
    onRoute: (AudioRoute) -> Unit = {},
    onHand: (Boolean) -> Unit = {},
    onReact: (CallReaction) -> Unit = {},
) {
    var reacting by remember { mutableStateOf(false) }
    if (reacting) {
        ReactionSheet(
            ui.handRaised,
            onHand = {
                reacting = false
                onHand(it)
            },
            onReact = {
                reacting = false
                onReact(it)
            },
            onDismiss = { reacting = false },
        )
    }
    val layout = remember { StageLayout() }
    val views = callViews(ui)
    // A shared screen takes the stage when it starts (unless something else is pinned).
    val firstScreen = views.firstOrNull { it.screen }?.key
    LaunchedEffect(firstScreen) { if (firstScreen != null && layout.pinned == null) layout.pinned = firstScreen }
    // Pinned someone who's gone: back to the room's layout.
    if (layout.pinned != null && views.none { it.key == layout.pinned }) layout.pinned = null
    val full = layout.fullScreen && layout.pinned != null
    BackHandler(enabled = full) { layout.fullScreen = false }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (full) {
                            Modifier
                        } else {
                            Modifier.statusBarsPadding().padding(
                                top = TOP_BAR_HEIGHT,
                                bottom = CONTROLS_HEIGHT
                            )
                        }
                    ),
            ) {
                Stage(ui, views, layout, full)
            }
            if (full) return@Box
            CallTopBar(
                ui,
                onMinimise,
                Modifier.align(Alignment.TopCenter),
                layout.takeIf { offersLayouts(ui, views) },
            )
            CallControls(
                ui,
                onMicrophone,
                onCamera,
                onFlipCamera,
                onHangUp,
                onRoute,
                onReactions = { reacting = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CallTopBar(
    ui: CallUi,
    onMinimise: () -> Unit,
    modifier: Modifier = Modifier,
    /** Offers grid ↔ spotlight when there's more than one thing to look at. */
    layout: StageLayout? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimise) {
            Icon(painterResource(R.drawable.ic_minimize), contentDescription = stringResource(R.string.call_minimise))
        }
        Column(Modifier.weight(1f)) {
            Text(
                ui.roomName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                callStatus(ui),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (layout != null) {
            val spotlight = layout.spotlight || layout.pinned != null
            IconButton(onClick = {
                layout.pinned = null
                layout.spotlight = !spotlight
            }) {
                Icon(
                    painterResource(if (spotlight) R.drawable.ic_grid else R.drawable.ic_spotlight),
                    contentDescription =
                        stringResource(
                            if (spotlight) R.string.call_layout_grid else R.string.call_layout_spotlight
                        ),
                )
            }
        }
    }
}

/** "Connecting…", "Calling…", the call's duration, or "Reconnecting…". */
@Composable
private fun callStatus(ui: CallUi): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val alone = ui.remote.none { it.participant.connected }
    return when (ui.phase) {
        null, CallPhase.Connecting -> {
            stringResource(R.string.call_connecting)
        }

        CallPhase.Reconnecting -> {
            stringResource(R.string.call_reconnecting)
        }

        is CallPhase.Ended -> {
            stringResource(R.string.call_ended)
        }

        CallPhase.Connected -> {
            if (alone && ui.isDirect) {
                stringResource(R.string.call_calling)
            } else if (alone) {
                stringResource(R.string.call_waiting)
            } else {
                formatDuration(now - (ui.connectedAt ?: now))
            }
        }
    }
}

/** What to tell someone whose call ended other than by hanging up. */
private fun endedMessage(phase: CallPhase.Ended): Int? =
    when (phase.reason) {
        EndReason.HungUp -> null
        EndReason.Declined -> R.string.call_declined
        EndReason.NoAnswer -> R.string.call_no_answer
        EndReason.Failed -> R.string.call_failed
    }

internal fun formatDuration(ms: Long): String {
    val total = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A phone call: the other person, big, with a ring that lights up while they speak. */
@Composable
internal fun DirectAudio(ui: CallUi) {
    val other = ui.remote.firstOrNull()
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpeakingAvatar(
            name = other?.name ?: ui.roomName,
            id = other?.participant?.userId ?: ui.roomName,
            avatarUrl = other?.avatarUrl ?: ui.roomAvatarUrl,
            speaking = other?.participant?.speaking == true,
            size = 160.dp,
        )
        other?.participant?.reaction?.let { FloatingReaction(it) }
        Spacer(Modifier.height(24.dp))
        Text(other?.name ?: ui.roomName, style = MaterialTheme.typography.headlineMedium)
        other?.participant?.handRaisedAt?.let { HandBadge(it, Modifier.padding(top = 8.dp)) }
        if (other != null && !other.participant.microphoneOn && other.participant.connected) {
            Icon(
                painterResource(R.drawable.ic_mic_off),
                contentDescription = stringResource(R.string.call_muted),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A video call with one person: them filling the screen, us in a corner. */
@Composable
internal fun DirectVideo(ui: CallUi) {
    val other = ui.remote.firstOrNull()
    val me = ui.local
    Box(Modifier.fillMaxSize()) {
        if (other != null) Tile(other, Modifier.fillMaxSize(), rounded = false)
        if (me?.participant?.video != null) {
            Tile(
                me,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .width(112.dp)
                    .aspectRatio(SELF_VIEW_RATIO),
            )
        }
    }
}

@Composable
internal fun Tile(
    tile: CallTile,
    modifier: Modifier = Modifier,
    rounded: Boolean = true,
    /** Their shared screen rather than their camera. */
    screen: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = tile.participant
    val ring by animateColorAsState(
        if (p.speaking && !screen) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "speaking"
    )
    val shape = if (rounded) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (screen) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(3.dp, ring, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        val video = if (screen) p.screen else p.video
        val room = p.videoRoom
        if (video != null && room != null) {
            VideoView(video, room, mirror = p.isLocal && !screen, modifier = Modifier.fillMaxSize(), fit = screen)
        } else {
            RoomAvatar(tile.name, p.userId, tile.avatarUrl, Modifier.align(Alignment.Center), size = 88.dp)
        }
        if (!screen) {
            p.handRaisedAt?.let { HandBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
            p.reaction?.let { FloatingReaction(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) }
        }
        TileLabel(tile, screen, Modifier.align(Alignment.BottomStart))
    }
}

/** Who a tile is (and whether they're muted, or it's their screen), in its corner. */
@Composable
private fun TileLabel(
    tile: CallTile,
    screen: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = tile.participant
    Row(
        modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (screen) {
            Icon(
                painterResource(R.drawable.ic_screen_share),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).padding(end = 2.dp),
            )
        } else if (!p.microphoneOn) {
            Icon(
                painterResource(R.drawable.ic_mic_off),
                contentDescription = stringResource(R.string.call_muted),
                tint = Color.White,
                modifier = Modifier.size(16.dp).padding(end = 2.dp),
            )
        }
        Text(
            when {
                screen -> {
                    stringResource(
                        R.string.call_screen_of,
                        if (p.isLocal) stringResource(R.string.call_you) else tile.name
                    )
                }

                p.isLocal -> {
                    stringResource(R.string.call_you)
                }

                else -> {
                    tile.name
                }
            },
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeakingAvatar(
    name: String,
    id: String,
    avatarUrl: String?,
    speaking: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    val ring by animateColorAsState(
        if (speaking) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "speaking"
    )
    Box(Modifier.border(4.dp, ring, CircleShape).padding(8.dp)) {
        RoomAvatar(name, id, avatarUrl, size = size)
    }
}

@Composable
private fun CallControls(
    ui: CallUi,
    onMicrophone: (Boolean) -> Unit,
    onCamera: (Boolean) -> Unit,
    onFlipCamera: () -> Unit,
    onHangUp: () -> Unit,
    onRoute: (AudioRoute) -> Unit,
    onReactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().height(CONTROLS_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconToggleButton(
            checked = !ui.microphoneOn,
            onCheckedChange = { onMicrophone(!ui.microphoneOn) },
            modifier = Modifier.size(CONTROL_SIZE),
        ) {
            Icon(
                painterResource(if (ui.microphoneOn) R.drawable.ic_mic else R.drawable.ic_mic_off),
                contentDescription = stringResource(if (ui.microphoneOn) R.string.call_mute else R.string.call_unmute),
            )
        }
        FilledTonalIconToggleButton(
            checked = ui.cameraOn,
            onCheckedChange = onCamera,
            modifier = Modifier.size(CONTROL_SIZE),
        ) {
            Icon(
                painterResource(if (ui.cameraOn) R.drawable.ic_videocam else R.drawable.ic_videocam_off),
                contentDescription =
                    stringResource(
                        if (ui.cameraOn) R.string.call_camera_off else R.string.call_camera_on
                    ),
            )
        }
        if (ui.routes.size > 1) RouteButton(ui, onRoute)
        FilledTonalIconToggleButton(checked = ui.handRaised, onCheckedChange = {
            onReactions()
        }, modifier = Modifier.size(CONTROL_SIZE)) {
            Icon(painterResource(R.drawable.ic_mood), contentDescription = stringResource(R.string.call_reactions))
        }
        if (ui.cameraOn) {
            FilledTonalIconToggleButton(
                checked = false,
                onCheckedChange = { onFlipCamera() },
                modifier = Modifier.size(CONTROL_SIZE)
            ) {
                Icon(
                    painterResource(R.drawable.ic_cameraswitch),
                    contentDescription = stringResource(R.string.call_flip_camera)
                )
            }
        }
        FilledIconButton(
            onClick = onHangUp,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            modifier = Modifier.width(72.dp).height(CONTROL_SIZE),
        ) {
            Icon(painterResource(R.drawable.ic_call_end), contentDescription = stringResource(R.string.call_hang_up))
        }
    }
}

/**
 * Where the call's audio goes. Two choices (earpiece and speaker) swap with a tap; with a headset or
 * a car in the mix, a menu lists them all.
 */
@Composable
private fun RouteButton(
    ui: CallUi,
    onRoute: (AudioRoute) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconToggleButton(
            checked = ui.route?.kind == RouteKind.Speaker,
            onCheckedChange = {
                if (ui.routes.size == 2) {
                    ui.routes.firstOrNull { it != ui.route }?.let(onRoute)
                } else {
                    menu = true
                }
            },
            modifier = Modifier.size(CONTROL_SIZE),
        ) {
            Icon(
                painterResource(routeIcon(ui.route?.kind)),
                contentDescription = stringResource(R.string.call_audio_route)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ui.routes.forEach { route ->
                DropdownMenuItem(
                    text = { Text(route.name) },
                    leadingIcon = { Icon(painterResource(routeIcon(route.kind)), null) },
                    onClick = {
                        menu = false
                        onRoute(route)
                    },
                )
            }
        }
    }
}

private fun routeIcon(kind: RouteKind?) =
    when (kind) {
        RouteKind.Speaker -> R.drawable.ic_speaker
        RouteKind.Bluetooth -> R.drawable.ic_bluetooth
        RouteKind.WiredHeadset -> R.drawable.ic_headset
        else -> R.drawable.ic_call
    }

private val TOP_BAR_HEIGHT = 64.dp
private val CONTROL_SIZE = 52.dp
private val CONTROLS_HEIGHT = 96.dp
private const val SELF_VIEW_RATIO = 0.75f
private const val TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
