package pt.aguiarvieira.xmuks.feature.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/**
 * Something shared to xmuks from another app: pick a room (unless it was shared straight to
 * one), give each file its own caption, send. Text alone goes to the room's composer.
 */
@Composable
fun ShareRoute(
    request: ShareRequest,
    onDone: (roomId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareViewModel =
        hiltViewModel<ShareViewModel, ShareViewModel.Factory>(
            key = request.hashCode().toString()
        ) { it.create(request) },
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val room by viewModel.room.collectAsStateWithLifecycle()
    val unreadable by viewModel.unreadable.collectAsStateWithLifecycle()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val chosen = room?.let { id -> rooms?.firstOrNull { it.roomId == id } }
    if (room == null) {
        RoomPicker(rooms, { viewModel.query.value = it }, { viewModel.choose(it) }, { onDone(null) }, modifier)
    } else {
        // Back from the captions goes to the picker, unless the room was given by the share.
        BackHandler { if (request.roomId == null) viewModel.choose(null) else onDone(null) }
        CaptionScreen(
            room = chosen,
            items = items,
            text = viewModel.text,
            unreadable = unreadable,
            gallery = gallery,
            onChangeRoom = { viewModel.choose(null) },
            onRemove = viewModel::remove,
            onSend = { captions -> scope.launch { onDone(viewModel.send(captions)) } },
            onBack = { if (request.roomId == null) viewModel.choose(null) else onDone(null) },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPicker(
    rooms: List<RoomSummary>?,
    onQuery: (String) -> Unit,
    onChoose: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val search = rememberTextFieldState()
    val query by rememberUpdatedState(onQuery)
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect { query(it) } }
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopBar(stringResource(R.string.share_to), onBack)
                    OutlinedTextField(
                        state = search,
                        placeholder = { Text(stringResource(R.string.share_search)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        },
    ) { padding ->
        ScreenCard(
            Modifier
                .padding(padding)
                .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
                .imePadding()
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            RoomList(rooms, onChoose)
        }
    }
}

@Composable
private fun RoomList(
    rooms: List<RoomSummary>?,
    onChoose: (String) -> Unit,
) {
    when {
        rooms == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        rooms.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.share_no_rooms), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        else -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(rooms, key = { it.roomId }) { room ->
                    ListItem(
                        leadingContent = { RoomAvatar(room.name, room.roomId, room.avatarUrl, size = 40.dp) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onChoose(room.roomId) },
                    ) { Text(room.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
        },
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** The chosen room, each file with its own caption (or a gallery's one), and Send. */
@Composable
internal fun CaptionScreen(
    room: RoomSummary?,
    items: List<SharedItem>?,
    text: String?,
    unreadable: List<String>,
    /** The files go as one gallery: one caption for them all, under [ShareViewModel.GALLERY_CAPTION]. */
    gallery: Boolean,
    onChangeRoom: () -> Unit,
    onRemove: (String) -> Unit,
    onSend: (Map<String, String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Captions by item; the shared text starts as the first one's.
    val captions = remember { mutableStateMapOf<String, TextFieldState>() }
    items?.forEachIndexed { index, item ->
        if (item.id !in captions) captions[item.id] = TextFieldState(if (index == 0) text.orEmpty() else "")
    }
    val galleryCaption = remember { TextFieldState(text.orEmpty()) }
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        topBar = {
            ScreenCard(Modifier.statusBarsPadding().padding(ScreenCards.Gap)) {
                Column {
                    TopBar(room?.name.orEmpty(), onBack)
                    RoomLine(room, items?.size, onChangeRoom)
                }
            }
        },
        floatingActionButton = {
            if (items != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onSend(
                            if (gallery) {
                                mapOf(ShareViewModel.GALLERY_CAPTION to galleryCaption.text.toString())
                            } else {
                                captions.mapValues { it.value.text.toString() }
                            },
                        )
                    },
                    icon = { Icon(painterResource(R.drawable.ic_send), null) },
                    text = { Text(stringResource(R.string.share_send)) },
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        when {
            items == null -> {
                Column(
                    Modifier.padding(padding).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.share_preparing))
                }
            }

            else -> {
                LazyColumn(
                    Modifier
                        .padding(
                            padding
                        ).padding(horizontal = ScreenCards.Gap)
                        .imePadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(ScreenCards.Gap),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(unreadable) { name ->
                        Text(
                            stringResource(R.string.share_unreadable, name),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    if (items.isEmpty() && text != null) {
                        item(
                            key = "text"
                        ) { ScreenCard(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(20.dp)) } }
                    }
                    if (gallery) item(key = "gallery") { GalleryCaptionCard(galleryCaption) }
                    items(items, key = { it.id }) { item ->
                        captions[item.id]?.let { ItemCard(item, it.takeUnless { gallery }, { onRemove(item.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomLine(
    room: RoomSummary?,
    count: Int?,
    onChange: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (room != null) RoomAvatar(room.name, room.roomId, room.avatarUrl, size = 28.dp)
        Text(
            count?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.share_count, it, it) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChange) { Text(stringResource(R.string.share_change_room)) }
    }
}

/** Sending as a gallery: what says so, and its one caption. */
@Composable
private fun GalleryCaptionCard(caption: TextFieldState) {
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.share_as_gallery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state = caption,
                placeholder = { Text(stringResource(R.string.share_gallery_caption)) },
                lineLimits = TextFieldLineLimits.MultiLine(1, CAPTION_LINES),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One file: a look at it, its name, its caption (none in a gallery); and taking it out of the share. */
@Composable
private fun ItemCard(
    item: SharedItem,
    caption: TextFieldState?,
    onRemove: () -> Unit,
) {
    ScreenCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumbnail(item)
                Text(
                    item.file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRemove
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.share_remove)) }
            }
            if (caption != null) {
                OutlinedTextField(
                    state = caption,
                    placeholder = { Text(stringResource(R.string.share_caption)) },
                    lineLimits = TextFieldLineLimits.MultiLine(1, CAPTION_LINES),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(item: SharedItem) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(THUMB).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (item.preview != null) {
            AsyncImage(item.preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val icon =
            when (item.file.kind) {
                MediaKind.Video -> R.drawable.ic_play
                MediaKind.Audio -> R.drawable.ic_audio
                MediaKind.File -> R.drawable.ic_file
                MediaKind.Image -> null
            }
        // White over a picture, the card's muted colour on its own.
        val tint = if (item.preview != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) Icon(painterResource(icon), null, tint = tint)
    }
}

private val THUMB = 72.dp
private const val CAPTION_LINES = 5
