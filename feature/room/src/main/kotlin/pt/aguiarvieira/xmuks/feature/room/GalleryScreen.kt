package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.GalleryItem
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.util.Blurhash
import java.text.DateFormat
import java.util.Date

@Composable
fun GalleryRoute(
    roomId: String,
    onBack: () -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel =
        hiltViewModel<GalleryViewModel, GalleryViewModel.Factory>(key = "gallery:$roomId") { it.create(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver =
        remember(viewModel) { MediaResolver(viewModel.media::avatar, viewModel.media::media, viewModel.mediaImages) }
    val save = rememberMediaSaver()
    GalleryScreen(
        state,
        resolver,
        onFilter = viewModel::filter,
        onLoadMore = viewModel::loadMore,
        onOpen = { item -> openOrSave(item, resolver, onOpenMedia, save) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Pictures and videos open in the viewer; audio and files are saved where the user picks. */
private fun openOrSave(
    item: GalleryItem,
    resolver: MediaResolver,
    onOpenMedia: (ViewerMedia) -> Unit,
    save: (Media) -> Unit,
) {
    when (val c = item.content) {
        is MessageContent.Image -> {
            onOpenMedia(galleryViewer(item, c.media, ViewerMedia.Kind.Image, resolver))
        }

        is MessageContent.Video -> {
            onOpenMedia(galleryViewer(item, c.media, ViewerMedia.Kind.Video, resolver))
        }

        is MessageContent.Audio -> {
            save(c.media)
        }

        is MessageContent.File -> {
            save(c.media)
        }

        else -> {}
    }
}

private fun galleryViewer(
    item: GalleryItem,
    media: Media,
    kind: ViewerMedia.Kind,
    resolver: MediaResolver,
) = ViewerMedia(
    kind = kind,
    url = resolver.media(media.mxc, media.encrypted).orEmpty(),
    previewUrl = timelineSource(media, kind, resolver),
    blurhash = media.blurhash,
    width = media.width,
    height = media.height,
    title = item.senderName,
    subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.timestamp)),
)

/**
 * A room's media as a grid, newest first, loading older as the end comes into view. Pinching
 * changes how many columns there are; chips narrow it to pictures and videos, audio, or files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryState,
    resolver: MediaResolver,
    onFilter: (GalleryFilter) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (GalleryItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = { Text(stringResource(R.string.gallery)) },
                    )
                    Row(
                        Modifier
                            .horizontalScroll(
                                rememberScrollState()
                            ).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GalleryFilter.entries.forEach { f ->
                            FilterChip(f == state.filter, { onFilter(f) }, { Text(stringResource(labelOf(f))) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(padding)
                .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            GalleryGrid(state, resolver, onLoadMore, onOpen)
        }
    }
}

@Composable
private fun GalleryGrid(
    state: GalleryState,
    resolver: MediaResolver,
    onLoadMore: () -> Unit,
    onOpen: (GalleryItem) -> Unit,
) {
    var columns by rememberSaveable { mutableIntStateOf(DEFAULT_COLUMNS) }
    val grid = rememberLazyGridState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            val last =
                grid.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - NEAR_END
        }
    }
    LaunchedEffect(nearEnd, state.items.size, state.filter) { if (nearEnd && !state.done) loadMore() }
    val shown = state.shown
    if (shown.isEmpty() && !state.loading) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                state.error ?: stringResource(R.string.gallery_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = grid,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .pinchColumns { fewer ->
                    columns = (columns + if (fewer) -1 else 1).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                },
    ) {
        items(shown, key = { it.key }) { item -> GalleryTile(item, resolver) { onOpen(item) } }
        if (state.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Two fingers spreading ([fewer] columns, bigger tiles) or pinching; one finger still scrolls. */
private fun Modifier.pinchColumns(onStep: (fewer: Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    when {
                        zoom > STEP_IN -> {
                            onStep(true)
                            zoom = 1f
                        }

                        zoom < STEP_OUT -> {
                            onStep(false)
                            zoom = 1f
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** A square: the picture (a video's with a play badge), or an icon and name for audio and files. */
@Composable
private fun GalleryTile(
    item: GalleryItem,
    resolver: MediaResolver,
    onClick: () -> Unit,
) {
    val (media, kind) =
        when (val c = item.content) {
            is MessageContent.Image -> c.media to ViewerMedia.Kind.Image
            is MessageContent.Video -> c.media to ViewerMedia.Kind.Video
            is MessageContent.Audio -> c.media to null
            is MessageContent.File -> c.media to null
            else -> return
        }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .tapOrHold(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val source = kind?.let { timelineSource(media, it, resolver) }
        if (source != null && media.spoiler) {
            // Hidden here too: its blurhash and the badge; opening it is the tap that shows it.
            val blur = remember(media.blurhash) { media.blurhash?.let { Blurhash.decode(it)?.asImageBitmap() } }
            blur?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            SpoilerBadge(null)
        } else if (source != null) {
            val loader = resolver.images
            if (loader != null) {
                AsyncImage(source, null, loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                AsyncImage(source, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (kind == ViewerMedia.Kind.Video) PlayBadge()
        } else {
            FileTile(item, media)
        }
    }
}

@Composable
private fun FileTile(
    item: GalleryItem,
    media: Media,
) {
    val icon =
        when (item.content) {
            is MessageContent.Audio -> R.drawable.ic_audio
            is MessageContent.Video -> R.drawable.ic_videocam
            else -> R.drawable.ic_file
        }
    Column(
        Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            media.name.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun labelOf(filter: GalleryFilter) =
    when (filter) {
        GalleryFilter.All -> R.string.gallery_all
        GalleryFilter.Visual -> R.string.gallery_visual
        GalleryFilter.Audio -> R.string.gallery_audio
        GalleryFilter.Files -> R.string.gallery_files
    }

private const val DEFAULT_COLUMNS = 3
private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 6
private const val NEAR_END = 12
private const val STEP_IN = 1.3f
private const val STEP_OUT = 0.77f
