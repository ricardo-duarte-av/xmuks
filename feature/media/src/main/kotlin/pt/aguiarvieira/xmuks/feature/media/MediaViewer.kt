package pt.aguiarvieira.xmuks.feature.media

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import pt.aguiarvieira.xmuks.core.data.media.PdfFiles
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash
import kotlin.math.abs

/**
 * Full-screen viewer for any [ViewerMedia]. Images zoom (pinch, double tap) and pan; video and audio
 * stream with the platform player UI. A tap toggles the chrome (and the system bars). The top bar
 * turns the picture, saves the file, and — for a message's media — forwards it or goes to it. A
 * message's picture or video swipes to the room's others: older to the left, newer to the right.
 */
@Composable
fun MediaViewerRoute(
    media: ViewerMedia,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Forwarding a message's media to another room (with a caption of its own). */
    onForward: ((ViewerMedia) -> Unit)? = null,
    /** Opening the room at the message the media is from. */
    onGoToMessage: ((roomId: String, eventId: String) -> Unit)? = null,
    viewModel: MediaViewerViewModel = hiltViewModel(key = media.url),
) {
    val save = rememberViewerSaver(viewModel)
    LaunchedEffect(media) { viewModel.open(media) }
    val sequence by viewModel.sequence.collectAsStateWithLifecycle()
    MediaViewer(
        media,
        onBack,
        viewModel::player,
        modifier,
        viewModel.images,
        sequence = sequence,
        onNearStart = viewModel::loadOlder,
        pageCount = viewModel::pageCount,
        actions =
            ViewerActions(
                onSave = save,
                onForward = onForward,
                onGoToMessage =
                    onGoToMessage?.let { go ->
                        { m -> if (m.roomId != null && m.eventId != null) go(m.roomId, m.eventId) }
                    },
            ),
    )
}

/** What the top bar does with what's shown; an action left null isn't offered. */
class ViewerActions(
    val onSave: ((ViewerMedia) -> Unit)? = null,
    val onForward: ((ViewerMedia) -> Unit)? = null,
    val onGoToMessage: ((ViewerMedia) -> Unit)? = null,
)

/** The viewer itself; [playerFor] supplies the player for video and audio. */
@Composable
fun MediaViewer(
    media: ViewerMedia,
    onBack: () -> Unit,
    playerFor: (url: String) -> Player,
    modifier: Modifier = Modifier,
    /** The cache tier to load pictures through; null uses the app's default loader. */
    images: ImageLoader? = null,
    actions: ViewerActions = ViewerActions(),
    /** The room's pictures and videos around [media], oldest first; null to show it alone. */
    sequence: List<ViewerMedia>? = null,
    /** The oldest of [sequence] is near: time for older ones. */
    onNearStart: () -> Unit = {},
    /** A PDF's number of pages; 0 when it can't be read. */
    pageCount: suspend (url: String) -> Int = { 0 },
) {
    var chrome by rememberSaveable { mutableStateOf(true) }
    ImmersiveWhile(hidden = !chrome)
    val pages = sequence ?: listOf(media)
    // From the one opened to the many around it: start on it.
    val pager =
        key(sequence != null) {
            rememberPagerState(pages.indexOfFirst { it.pageKey == media.pageKey }.coerceAtLeast(0)) { pages.size }
        }
    val current = pages.getOrElse(pager.currentPage) { media }
    // Quarter turns clockwise, for a picture taken sideways; each page starts upright.
    var turns by rememberSaveable(current.pageKey) { mutableIntStateOf(0) }
    val nearStart by rememberUpdatedState(onNearStart)
    LaunchedEffect(pager, pages.size) {
        snapshotFlow { pager.currentPage }.collect { if (it < NEAR_START) nearStart() }
    }
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { pages[it].pageKey },
            userScrollEnabled = pages.size > 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val shown = page == pager.settledPage
            Page(
                pages[page],
                shown = shown,
                turns = if (shown) turns else 0,
                playerFor = playerFor,
                images = images,
                pageCount = pageCount,
                onTap = { chrome = !chrome },
                onSwipe = { step -> scope.launch { pager.animateScrollToPage(page + step) } },
            )
        }
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
            // Up top, clear of the player's controls at the bottom.
            TopBar(current, onBack) { Actions(current, actions, onRotate = { turns = (turns + 1) % QUARTERS }) }
        }
    }
}

/**
 * One page: the picture zooms, the video or audio plays while it's the page [shown] (the others
 * show their preview). A player takes the touches it gets, so swiping over one is watched for here.
 */
@Composable
private fun Page(
    media: ViewerMedia,
    shown: Boolean,
    turns: Int,
    playerFor: (url: String) -> Player,
    images: ImageLoader?,
    pageCount: suspend (url: String) -> Int,
    onTap: () -> Unit,
    onSwipe: (step: Int) -> Unit,
) {
    val playable = media.kind == ViewerMedia.Kind.Video || media.kind == ViewerMedia.Kind.Audio
    Box(Modifier.fillMaxSize().then(if (playable && shown) Modifier.swipes(onSwipe) else Modifier)) {
        Box(Modifier.fillMaxSize().rotated(turns)) {
            when (media.kind) {
                ViewerMedia.Kind.Image -> {
                    ZoomableImage(media, images, onTap = onTap)
                }

                ViewerMedia.Kind.Video, ViewerMedia.Kind.Audio -> {
                    if (shown) {
                        MediaPlayer(media, playerFor, images)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Preview(media, images ?: SingletonImageLoader.get(LocalPlatformContext.current))
                        }
                    }
                }

                ViewerMedia.Kind.Pdf -> {
                    PdfDocument(media, images, pageCount, onTap)
                }

                ViewerMedia.Kind.File -> {}
            }
        }
    }
}

/** A PDF's pages, one per screen (scrolling down), each zooming like a picture; which page it is. */
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfDocument(
    media: ViewerMedia,
    images: ImageLoader?,
    pageCount: suspend (url: String) -> Int,
    onTap: () -> Unit,
) {
    val loader = images ?: SingletonImageLoader.get(LocalPlatformContext.current)
    val countPages by rememberUpdatedState(pageCount)
    val count by produceState<Int?>(null, media.url) { value = countPages(media.url) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val pages = count) {
            null -> {
                ContainedLoadingIndicator()
            }

            0 -> {
                Text(stringResource(R.string.media_failed), color = Color.White)
            }

            else -> {
                val pager = rememberPagerState { pages }
                VerticalPager(pager, Modifier.fillMaxSize()) { page ->
                    ZoomableAsyncImage(
                        model = PdfFiles.page(media.url, page),
                        contentDescription = null,
                        state = rememberZoomableImageState(),
                        imageLoader = loader,
                        onClick = { onTap() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    stringResource(R.string.pdf_page, pager.currentPage + 1, pages),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = SCRIM), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * A sideways swipe (mostly across, far enough) as a step to the next page (-1: the previous),
 * watched before the content sees it and left for it all the same: a player's view keeps its taps.
 */
private fun Modifier.swipes(onSwipe: (step: Int) -> Unit): Modifier =
    pointerInput(onSwipe) {
        val far = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var last = down.position
            var pressed = true
            while (pressed) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                pressed = change?.pressed == true
                if (change != null) last = change.position
            }
            val across = last.x - down.position.x
            val along = last.y - down.position.y
            if (abs(across) > far && abs(across) > abs(along) * 2) onSwipe(if (across < 0) 1 else -1)
        }
    }

/**
 * Turns the content by [turns] quarter turns, laid out in the space it has once turned: a
 * landscape picture turned upright fills a portrait screen. Touches turn with it.
 */
private fun Modifier.rotated(turns: Int): Modifier {
    if (turns % QUARTERS == 0) return this
    val sideways = turns % 2 == 1
    return layout { measurable, constraints ->
        val inner =
            if (sideways) {
                Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth)
            } else {
                constraints
            }
        val placeable = measurable.measure(inner)
        val width = if (sideways) placeable.height else placeable.width
        val height = if (sideways) placeable.width else placeable.height
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                rotationZ = turns * DEGREES_PER_TURN
            }
        }
    }
}

// Kotlin's opt-in, not androidx.annotation.OptIn (imported here for Media3's UnstableApi).
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZoomableImage(
    media: ViewerMedia,
    images: ImageLoader?,
    onTap: () -> Unit,
) {
    val loader = images ?: SingletonImageLoader.get(LocalPlatformContext.current)
    val state = rememberZoomableImageState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // What was already on screen (thumbnail, else blurhash) stands in — and carries the shared
        // element — until the original is displayed.
        if (!state.isImageDisplayed) Preview(media, loader)
        ZoomableAsyncImage(
            model = media.url,
            contentDescription = media.title,
            state = state,
            imageLoader = loader,
            onClick = { onTap() },
            modifier = Modifier.fillMaxSize(),
        )
        // The preview is up but the original is still downloading: say so.
        if (!state.isImageDisplayed) {
            ContainedLoadingIndicator(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
        }
    }
}

@Composable
private fun Preview(
    media: ViewerMedia,
    loader: ImageLoader,
) {
    val ratio = ratioOf(media)
    val shared = media.sharedKey?.let { Modifier.sharedElement(it) } ?: Modifier
    val frame = shared.fillMaxWidth().then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
    val blur = remember(media.blurhash) { media.blurhash?.let { Blurhash.decode(it)?.asImageBitmap() } }
    Box(frame) {
        blur?.let { Image(it, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        media.previewUrl?.let {
            AsyncImage(
                it,
                null,
                loader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun ratioOf(media: ViewerMedia): Float? {
    val w = media.width?.takeIf { it > 0 } ?: return null
    val h = media.height?.takeIf { it > 0 } ?: return null
    return w.toFloat() / h
}

@OptIn(UnstableApi::class)
@Composable
private fun MediaPlayer(
    media: ViewerMedia,
    playerFor: (url: String) -> Player,
    images: ImageLoader?,
) {
    val player = remember(media.url) { playerFor(media.url) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    // Swiped away: it stops (the next video takes the player over).
    DisposableEffect(player) { onDispose { player.pause() } }
    val audio = media.kind == ViewerMedia.Kind.Audio
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!audio) Preview(media, images ?: SingletonImageLoader.get(LocalPlatformContext.current))
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    if (audio) {
                        // Nothing to look at: keep the controls up.
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                    }
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TopBar(
    media: ViewerMedia,
    onBack: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = Color.White)
        }
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            media.title?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            media.subtitle?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = SUBTLE),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
        actions()
    }
}

/** Turning the picture, saving it, and for a message's media forwarding it and going to it. */
@Composable
private fun Actions(
    media: ViewerMedia,
    actions: ViewerActions,
    onRotate: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Audio has nothing to turn.
        if (media.kind != ViewerMedia.Kind.Audio) BarButton(R.drawable.ic_rotate, R.string.rotate, onRotate)
        actions.onSave?.let { save -> BarButton(R.drawable.ic_download, R.string.save) { save(media) } }
        if (media.fromMessage) {
            actions.onForward?.let { forward -> BarButton(R.drawable.ic_forward, R.string.forward) { forward(media) } }
            actions.onGoToMessage?.let { go -> BarButton(R.drawable.ic_chat, R.string.go_to_message) { go(media) } }
        }
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), stringResource(label), tint = Color.White)
    }
}

/** Hides the system bars while [hidden], light icons on black otherwise; restores both on leaving. */
@Composable
private fun ImmersiveWhile(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
    DisposableEffect(hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(
                WindowInsetsCompat.Type.systemBars()
            )
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private const val SCRIM = 0.6f
private const val QUARTERS = 4

/** Pages from the oldest loaded at which older ones are fetched. */
private const val NEAR_START = 3
private val SWIPE_DISTANCE = 64.dp
private const val DEGREES_PER_TURN = 90f
private const val SUBTLE = 0.75f
