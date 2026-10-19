package pt.aguiarvieira.xmuks.feature.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Action
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.media
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/**
 * The composer's panel grown to full height, over the panel itself: the same tabs and buttons, the
 * full-screen one now shrinking it back, as back and a swipe down do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandedPicker(
    request: PickerRequest,
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
) {
    val shrink = { state.picker = PickerRequest(request.mode) }
    ModalBottomSheet(
        onDismissRequest = shrink,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        var searching by remember(request.mode) { mutableStateOf(false) }
        Column(Modifier.fillMaxHeight()) {
            PickerHeader(
                request.mode,
                onModeChange = { state.picker = PickerRequest(it, expanded = true) },
                expanded = true,
                onToggleFullScreen = shrink,
                searching = searching,
                onToggleSearch = { searching = !searching },
            )
            PickerContent(request, composer, resolver, searching) { state.picker = null }
        }
    }
}

/** Emoji into the draft: Unicode as is, custom ones as `:shortcode:` (expanded when sent). */
private fun Picked.asText(): String =
    when (this) {
        is Picked.Unicode -> emoji
        is Picked.Custom -> ":${image.shortcode}:"
    }

private fun TextFieldState.insertAtCursor(text: String) {
    edit { replace(selection.min, selection.max, text) }
}

/**
 * The emoji/sticker picker in place of the keyboard, under the message box, so the box and the
 * timeline move up exactly as they do for the keyboard. It takes the keyboard's last height (or a
 * shorter one while its own search field has the keyboard up).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerPanel(
    request: PickerRequest,
    composer: ComposerActions,
    resolver: MediaResolver,
    keyboardHeight: Dp,
    onModeChange: (PickerMode) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val searching = WindowInsets.isImeVisible
    ScreenCard(
        Modifier
            .fillMaxWidth()
            .padding(start = ScreenCards.Gap, end = ScreenCards.Gap, bottom = ScreenCards.Gap)
            // The card's bottom margin counts against the keyboard's height, so the box doesn't move.
            .height(if (searching) SEARCHING_HEIGHT else (keyboardHeight - ScreenCards.Gap).coerceAtLeast(MIN_PANEL)),
    ) {
        var searching by remember(request.mode) { mutableStateOf(false) }
        Column {
            PickerHeader(
                request.mode,
                onModeChange,
                expanded = false,
                onToggleFullScreen = onExpand,
                searching = searching,
                onToggleSearch = { searching = !searching },
            )
            PickerContent(request, composer, resolver, searching, onClose)
        }
    }
}

/** The picker's tabs, full-screen toggle and search toggle: the same in the panel and grown. */
@Composable
private fun PickerHeader(
    mode: PickerMode,
    onModeChange: (PickerMode) -> Unit,
    expanded: Boolean,
    onToggleFullScreen: () -> Unit,
    searching: Boolean,
    onToggleSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryTabRow(
            selectedTabIndex = mode.ordinal,
            containerColor = Color.Transparent,
            modifier = Modifier.weight(1f)
        ) {
            PickerMode.entries.forEach { tab ->
                Tab(
                    selected = mode == tab,
                    onClick = { onModeChange(tab) },
                    text = {
                        Text(
                            stringResource(
                                if (tab ==
                                    PickerMode.Emoji
                                ) {
                                    R.string.emoji
                                } else {
                                    R.string.sticker_button
                                }
                            )
                        )
                    },
                )
            }
        }
        // A few rows in the keyboard's place is not much to choose from; grown, the same
        // button shrinks it back.
        IconButton(onClick = onToggleFullScreen) {
            Icon(
                painterResource(if (expanded) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                stringResource(if (expanded) R.string.fullscreen_exit else R.string.fullscreen),
            )
        }
        IconButton(onClick = onToggleSearch) {
            Icon(
                painterResource(if (searching) R.drawable.ic_close else R.drawable.ic_search),
                stringResource(
                    if (mode ==
                        PickerMode.Sticker
                    ) {
                        R.string.search_stickers
                    } else {
                        R.string.search_emoji
                    }
                ),
            )
        }
    }
}

@Composable
private fun PickerContent(
    request: PickerRequest,
    composer: ComposerActions,
    resolver: MediaResolver,
    searching: Boolean,
    onClose: () -> Unit,
) {
    // Keyed by mode: switching tabs starts a fresh search and scroll.
    key(request.mode) {
        EmojiPicker(
            mode = request.mode,
            packs = composer.emoji.packs,
            recent = composer.emoji.recent,
            resolver = resolver,
            onPick = { picked ->
                if (request.mode == PickerMode.Sticker) {
                    (picked as? Picked.Custom)?.let { composer.emoji.onSticker(it.image) }
                    onClose()
                } else {
                    // Stays open for more.
                    composer.draft.insertAtCursor(picked.asText())
                    composer.emoji.onUsed(picked)
                }
            },
            onSubscribe = composer.emoji.onSubscribe,
            modifier = Modifier.padding(top = 8.dp),
            showSearch = searching,
        )
    }
}

private val MIN_PANEL = 280.dp
private val SEARCHING_HEIGHT = 200.dp
