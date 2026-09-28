package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * The emoji/sticker picker: search, a strip of sections to jump to (recent, custom packs, the
 * Unicode groups), and the grid. Room packs can be subscribed to (or dropped) from their header.
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
            allowFreeform = true
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
) {
    val query = rememberTextFieldState()
    val q by remember { derivedStateOf { query.text.toString().trim() } }
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
            modifier = Modifier.fillMaxWidth(),
        )
        if (allowFreeform && q.isNotEmpty()) {
            TextButton(onClick = { onPick(Picked.Unicode(q)) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.react_with, q), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (q.isEmpty()) {
            LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(sections, key = { _, s -> s.id }) { i, section ->
                    Box(
                        Modifier
                            .size(TAB)
                            .clip(CircleShape)
                            .clickable { scope.launch { grid.scrollToItem(headerIndex[i]) } },
                        contentAlignment = Alignment.Center,
                    ) { SectionIcon(section, resolver) }
                }
            }
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
                        Cell(picked, resolver) { onPick(picked) }
                    }
                }
            }
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

@Composable
private fun Cell(
    picked: Picked,
    resolver: MediaResolver,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
        return listOf(Section("search", "", null, customHits + unicodeHits))
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
        packs.filter { it.emojis.isNotEmpty() }.forEach {
            add(
                Section(it.id, it.name, it.iconMxc, it.emojis.map(custom), it)
            )
        }
        catalog.emoji.groupBy { it.group }.toSortedMap().forEach { (group, emoji) ->
            val title = catalog.groups.getOrNull(group)?.replaceFirstChar { it.uppercase() } ?: ""
            add(Section("g$group", title, emoji.first().emoji, emoji.map { Picked.Unicode(it.emoji) }))
        }
    }
}

private val PICKER_HEIGHT = 480.dp
private val TAB = 40.dp
private val EMOJI_CELL = 44.dp
private val STICKER_CELL = 96.dp
private const val MAX_RECENT = 32
