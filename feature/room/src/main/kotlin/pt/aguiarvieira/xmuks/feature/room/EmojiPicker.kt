package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.emoji.ImagePack

/** One stretch of the grid: a pack, the recent row, or a Unicode group. */
private class Section(
    val id: String,
    val title: String,
    /** A Unicode emoji or an `mxc://` URI for the tab. */
    val icon: String?,
    val items: List<Picked>,
    val pack: ImagePack? = null,
)

/**
 * The emoji/sticker picker: search, a strip of sections to jump to (recent, the Unicode groups,
 * custom packs), and the grid. Room packs can be subscribed to (or dropped) from their header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerSheet(
    mode: PickerMode,
    packs: List<ImagePack>,
    recent: List<String>,
    resolver: MediaResolver,
    onPick: (Picked) -> Unit,
    onSubscribe: (ImagePack, Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** What the bridged network won't take as a reaction: dimmed (picking it says why). */
    blocked: (Picked) -> Boolean = { false },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Straight to full height: no half-open step.
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        EmojiPicker(
            mode,
            packs,
            recent,
            resolver,
            onPick,
            onSubscribe,
            Modifier.height(PICKER_HEIGHT),
            allowFreeform = true,
            focusSearch = false,
            blocked = blocked,
        )
    }
}

/** The picker itself: in a sheet (reactions) or in place of the keyboard (the composer). */
@Composable
internal fun EmojiPicker(
    mode: PickerMode,
    packs: List<ImagePack>,
    recent: List<String>,
    resolver: MediaResolver,
    onPick: (Picked) -> Unit,
    onSubscribe: (ImagePack, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Reactions may be any text: offer the search text itself, like gomuks web. */
    allowFreeform: Boolean = false,
    /** The search field; the composer's panel hides it until asked (it takes rows of emoji). */
    showSearch: Boolean = true,
    /** Search shown at once takes the keyboard; in a sheet it waits to be tapped. */
    focusSearch: Boolean = true,
    /** Shown dimmed: what the bridged network won't take (still picked, to say why). */
    blocked: (Picked) -> Boolean = { false },
) {
    val query = rememberTextFieldState()
    val q by remember(showSearch) { derivedStateOf { if (showSearch) query.text.toString().trim() else "" } }
    val focus = remember { FocusRequester() }
    // Asked for: straight into it, keyboard up.
    LaunchedEffect(showSearch) { if (showSearch && focusSearch) focus.requestFocus() }
    val sections = remember(mode, packs, recent, q) { sections(mode, packs, recent, q) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Grid index of each section's header, for the tab strip.
    val headerIndex =
        remember(sections) {
            var index = 0
            sections.map { section -> index.also { index += 1 + section.items.size } }
        }
    Column(modifier.padding(horizontal = 12.dp)) {
        if (showSearch) {
            OutlinedTextField(
                state = query,
                placeholder = {
                    Text(
                        stringResource(
                            if (mode ==
                                PickerMode.Sticker
                            ) {
                                R.string.search_stickers
                            } else {
                                R.string.search_emoji
                            }
                        )
                    )
                },
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (allowFreeform && q.isNotEmpty()) {
            TextButton(onClick = { onPick(Picked.Unicode(q)) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.react_with, q), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (q.isEmpty()) {
            // The section the grid is in: the last whose header is at or above the top row.
            val current by remember(headerIndex) {
                derivedStateOf { headerIndex.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) }
            }
            SectionTabs(sections, current, resolver) { i -> scope.launch { grid.scrollToItem(headerIndex[i]) } }
        }
        val cell = if (mode == PickerMode.Sticker) STICKER_CELL else EMOJI_CELL
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(cell),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            sections.forEach { section ->
                item(key = "h:" + section.id, span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section, onSubscribe)
                }
                section.items.forEachIndexed { i, picked ->
                    item(key = section.id + ":" + i + ":" + picked.key) {
                        Cell(picked, resolver, blocked, onPick)
                    }
                }
            }
        }
    }
}

/** The strip of sections to jump to; the one in view sits on a pill, like the home tab bar's. */
@Composable
private fun SectionTabs(
    sections: List<Section>,
    current: Int,
    resolver: MediaResolver,
    onTab: (Int) -> Unit,
) {
    val strip = rememberLazyListState()
    // Keep the highlighted tab on screen as the grid scrolls through the sections.
    LaunchedEffect(current) {
        val info = strip.layoutInfo
        val tab = info.visibleItemsInfo.firstOrNull { it.index == current }
        val fits =
            tab != null && tab.offset >= info.viewportStartOffset && tab.offset + tab.size <= info.viewportEndOffset
        if (!fits) strip.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = strip,
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(sections, key = { _, s -> s.id }) { i, section ->
            val pill by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                label = "tab",
            )
            Box(
                Modifier
                    .size(TAB_WIDTH, TAB_HEIGHT)
                    .clip(CircleShape)
                    .background(pill)
                    .clickable { onTab(i) },
                contentAlignment = Alignment.Center,
            ) { SectionIcon(section, resolver) }
        }
    }
}

@Composable
private fun SectionIcon(
    section: Section,
    resolver: MediaResolver,
) {
    val icon = section.icon
    when {
        icon == null -> {
            Icon(
                painterResource(R.drawable.ic_history),
                contentDescription = section.title,
                modifier = Modifier.size(22.dp)
            )
        }

        icon.startsWith(
            "mxc://"
        ) -> {
            AsyncImage(resolver.media(icon, false), section.title, Modifier.size(26.dp).clip(CircleShape))
        }

        else -> {
            Text(icon, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SectionHeader(
    section: Section,
    onSubscribe: (ImagePack, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            section.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val source = section.pack?.source as? ImagePack.Source.Room ?: return@Row
        TextButton(onClick = { onSubscribe(section.pack, !source.subscribed) }) {
            Text(stringResource(if (source.subscribed) R.string.unsubscribe_pack else R.string.subscribe_pack))
        }
    }
}

/**
 * One emoji or sticker. An emoji with skin tones carries a small mark in its corner, and a long
 * press offers them.
 */
@Composable
private fun Cell(
    picked: Picked,
    resolver: MediaResolver,
    blocked: (Picked) -> Boolean,
    onPick: (Picked) -> Unit,
) {
    val tones = remember(picked) { (picked as? Picked.Unicode)?.let { EmojiCatalog.tonesOf(it.emoji) }.orEmpty() }
    var choosing by remember { mutableStateOf(false) }
    val mark = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TONE_MARK_ALPHA)
    Box(
        Modifier
            .alpha(if (blocked(picked)) DISABLED_ALPHA else 1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onPick(picked) },
                onLongClickLabel = if (tones.isEmpty()) null else stringResource(R.string.emoji_skin_tones),
                onLongClick = if (tones.isEmpty()) null else ({ choosing = true }),
            ).then(if (tones.isEmpty()) Modifier else Modifier.toneMark(mark))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (picked) {
            is Picked.Unicode -> {
                Text(picked.emoji, fontSize = 26.sp)
            }

            is Picked.Custom -> {
                // The image itself, not gomuks' avatar thumbnail: thumbnails are always still frames.
                val url = resolver.media(picked.image.mxc, false)
                AsyncImage(url, picked.image.shortcode, Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        // Inside the cell, so the cell is its anchor: a lazy grid item's own siblings hang off the grid.
        if (choosing) {
            SkinTones(tones, blocked, onDismiss = { choosing = false }) {
                choosing = false
                onPick(it)
            }
        }
    }
}

/** A small triangle tucked into the cell's bottom-end corner, like a keyboard's long-press hint. */
private fun Modifier.toneMark(color: Color) =
    drawBehind {
        val side = TONE_MARK.toPx()
        val inset = TONE_MARK_INSET.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        val edge = if (rtl) inset else size.width - inset
        val inward = if (rtl) side else -side
        val bottom = size.height - inset
        val path =
            Path().apply {
                moveTo(edge, bottom)
                lineTo(edge, bottom - side)
                lineTo(edge + inward, bottom)
                close()
            }
        drawPath(path, color)
    }

/**
 * One emoji's tones floating above it (below, if there's no room): the default on its own, then a
 * divider, then the skin tones — one row of five, or a square of 25 for two-person emoji. It grows
 * and fades in out of the emoji that was pressed, and shrinks back into it when dismissed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SkinTones(
    tones: List<String>,
    blocked: (Picked) -> Boolean,
    onDismiss: () -> Unit,
    onPick: (Picked) -> Unit,
) {
    val anchor = remember { AboveAnchor() }
    val open = remember { Animatable(0f) }
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open.animateTo(1f, motion.fastSpatialSpec()) }
    Popup(
        popupPositionProvider = anchor,
        onDismissRequest = {
            if (!closing) {
                closing = true
                scope.launch {
                    open.animateTo(0f, motion.fastEffectsSpec())
                    onDismiss()
                }
            }
        },
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier =
                Modifier.graphicsLayer {
                    val p = open.value
                    val scale = POPUP_START_SCALE + (1f - POPUP_START_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = anchor.origin
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(4.dp)) {
                Tone(tones.first(), blocked, onPick)
                VerticalDivider(Modifier.height(EMOJI_CELL).padding(vertical = 8.dp, horizontal = 2.dp))
                Column {
                    tones.drop(1).chunked(SKIN_COLUMNS).forEach { row ->
                        Row { row.forEach { Tone(it, blocked, onPick) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tone(
    tone: String,
    blocked: (Picked) -> Boolean,
    onPick: (Picked) -> Unit,
) {
    val picked = Picked.Unicode(tone)
    Box(
        Modifier
            .size(EMOJI_CELL)
            .alpha(if (blocked(picked)) DISABLED_ALPHA else 1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPick(picked) },
        contentAlignment = Alignment.Center,
    ) { Text(tone, fontSize = 26.sp) }
}

/**
 * Centred over the anchor and just above it, kept inside the window. It remembers where the anchor
 * sits relative to the popup, [origin], so the popup can grow out of it.
 */
private class AboveAnchor : PopupPositionProvider {
    var origin by mutableStateOf(TransformOrigin(0.5f, 1f))
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            (anchorBounds.center.x - popupContentSize.width / 2)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        val fitsAbove = above >= 0
        val y = if (fitsAbove) above else anchorBounds.bottom
        val width = popupContentSize.width.coerceAtLeast(1)
        origin =
            TransformOrigin(
                ((anchorBounds.center.x - x).toFloat() / width).coerceIn(0f, 1f),
                if (fitsAbove) 1f else 0f,
            )
        return IntOffset(x, y)
    }
}

private fun sections(
    mode: PickerMode,
    packs: List<ImagePack>,
    recent: List<String>,
    query: String,
): List<Section> {
    val q = query.lowercase().removePrefix(":").removeSuffix(":")
    val custom = { image: pt.aguiarvieira.xmuks.core.data.emoji.PackImage -> Picked.Custom(image) }
    if (mode == PickerMode.Sticker) {
        return packs.filter { it.stickers.isNotEmpty() }.mapNotNull { pack ->
            val items =
                pack.stickers.filter {
                    q.isEmpty() || it.shortcode.lowercase().contains(q) ||
                        it.body.lowercase().contains(q)
                }
            if (items.isEmpty()) null else Section(pack.id, pack.name, pack.iconMxc, items.map(custom), pack)
        }
    }
    if (q.isNotEmpty()) {
        val customHits =
            packs
                .flatMap {
                    it.emojis
                }.filter { it.shortcode.lowercase().contains(q) }
                .distinctBy { it.mxc }
                .map(custom)
        val unicodeHits = EmojiCatalog.search(q).map { Picked.Unicode(it.emoji) }
        return listOf(Section("search", "", null, unicodeHits + customHits))
    }
    val byMxc = packs.flatMap { it.emojis }.associateBy { it.mxc }
    val recentItems =
        recent
            .mapNotNull { key ->
                if (key.startsWith("mxc://")) byMxc[key]?.let(custom) else Picked.Unicode(key)
            }.take(MAX_RECENT)
    val catalog = EmojiCatalog.catalog
    return buildList {
        if (recentItems.isNotEmpty()) add(Section("recent", "Recent", null, recentItems))
        catalog.emoji.groupBy { it.group }.toSortedMap().forEach { (group, emoji) ->
            val title = catalog.groups.getOrNull(group)?.replaceFirstChar { it.uppercase() } ?: ""
            add(Section("g$group", title, emoji.first().emoji, emoji.map { Picked.Unicode(it.emoji) }))
        }
        // The user's packs come after the default set.
        packs.filter { it.emojis.isNotEmpty() }.forEach {
            add(
                Section(it.id, it.name, it.iconMxc, it.emojis.map(custom), it)
            )
        }
    }
}

private val PICKER_HEIGHT = 480.dp
private val TAB_WIDTH = 48.dp
private val TAB_HEIGHT = 36.dp
private val EMOJI_CELL = 44.dp
private val STICKER_CELL = 96.dp
private const val MAX_RECENT = 32

/** Five tones a row: a two-person emoji's 25 make a square. */
private const val SKIN_COLUMNS = 5

/** The skin-tone hint in an emoji cell's corner: its side, its inset, and how strongly it shows. */
private val TONE_MARK = 6.dp
private val TONE_MARK_INSET = 3.dp
private const val TONE_MARK_ALPHA = 0.6f

/** How small the skin-tone popup starts, as it grows out of the pressed emoji. */
private const val POPUP_START_SCALE = 0.4f
