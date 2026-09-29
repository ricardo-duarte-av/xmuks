package pt.aguiarvieira.xmuks.feature.media

import android.app.Activity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash

/**
 * Full-screen viewer for any [ViewerMedia]. Images zoom (pinch, double tap) and pan; video and audio
 * stream with the platform player UI. A tap toggles the chrome (and the system bars).
 */
@Composable
fun MediaViewerRoute(
    media: ViewerMedia,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaViewerViewModel = hiltViewModel(key = media.url),
) = MediaViewer(media, onBack, viewModel::player, modifier, viewModel.images)

/** The viewer itself; [playerFor] supplies the player for video and audio. */
@Composable
fun MediaViewer(
    media: ViewerMedia,
    onBack: () -> Unit,
    playerFor: (url: String) -> Player,
    modifier: Modifier = Modifier,
    /** The cache tier to load pictures through; null uses the app's default loader. */
    images: ImageLoader? = null,
) {
    var chrome by rememberSaveable { mutableStateOf(true) }
    ImmersiveWhile(hidden = !chrome)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (media.kind) {
            ViewerMedia.Kind.Image -> ZoomableImage(media, images, onTap = { chrome = !chrome })
            ViewerMedia.Kind.Video, ViewerMedia.Kind.Audio -> MediaPlayer(media, playerFor, images)
        }
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
            TopBar(media, onBack)
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
        Column(Modifier.padding(start = 4.dp)) {
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
private const val SUBTLE = 0.75f
