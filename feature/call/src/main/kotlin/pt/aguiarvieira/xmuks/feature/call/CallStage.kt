package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.items as gridItems

/** One thing to look at in a call: someone's camera (or avatar), or a screen they share. */
internal data class CallView(
    val tile: CallTile,
    val screen: Boolean,
) {
    val key: String get() = tile.participant.key + if (screen) ":screen" else ""
}

/** Shared screens first (they're what people want to see), then everyone. */
internal fun callViews(ui: CallUi): List<CallView> =
    ui.tiles.filter { it.participant.screen != null }.map { CallView(it, screen = true) } +
        ui.tiles.map { CallView(it, screen = false) }

/**
 * How the call is laid out: all in a grid, or one in the spotlight (pinned, or full screen).
 * [spotlight] is the user's choice; null until they make one, when a shared screen brings the
 * spotlight on by itself.
 */
@Stable
internal class StageLayout {
    var spotlight by mutableStateOf<Boolean?>(null)
    var pinned by mutableStateOf<String?>(null)
    var fullScreen by mutableStateOf(false)

    /** Whether one view is in the spotlight: pinned, chosen, or (unchosen) someone else shares a screen. */
    fun spotlightOn(views: List<CallView>): Boolean =
        pinned != null || (spotlight ?: views.any { it.screen && !it.tile.participant.isLocal })
}

/**
 * One view large, the rest in a strip below (beside it, in landscape: a shared screen is wide).
 * Tapping the large one goes full screen (and back); tapping one in the strip puts it in the
 * spotlight. Unpinned, the spotlight follows whoever speaks, after any shared screen.
 */
@Composable
internal fun Spotlight(
    views: List<CallView>,
    layout: StageLayout,
) {
    val focus =
        views.firstOrNull { it.key == layout.pinned }
            ?: views.firstOrNull { it.screen && !it.tile.participant.isLocal }
            ?: views.firstOrNull { !it.screen && it.tile.participant.speaking && !it.tile.participant.isLocal }
            ?: views.firstOrNull { !it.tile.participant.isLocal }
            ?: views.firstOrNull()
            ?: return
    if (layout.fullScreen) {
        Tile(focus.tile, Modifier.fillMaxSize(), rounded = false, screen = focus.screen, onClick = {
            layout.fullScreen =
                false
        })
        return
    }
    val rest = views.filter { it.key != focus.key }
    val big: @Composable (Modifier) -> Unit = { modifier ->
        Tile(
            focus.tile,
            modifier,
            screen = focus.screen,
            onClick = {
                layout.pinned = focus.key
                layout.fullScreen = true
            },
        )
    }
    val small: @Composable (CallView, Modifier) -> Unit = { view, modifier ->
        Tile(view.tile, modifier, screen = view.screen, onClick = { layout.pinned = view.key })
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                big(Modifier.weight(1f).fillMaxHeight())
                if (rest.isNotEmpty()) {
                    LazyColumn(
                        Modifier.fillMaxHeight().width(STRIP_WIDTH),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rest, key = { it.key }) { view ->
                            small(view, Modifier.width(STRIP_WIDTH).aspectRatio(SIDE_RATIO))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                big(Modifier.weight(1f).fillMaxWidth())
                if (rest.isNotEmpty()) {
                    LazyRow(
                        Modifier.fillMaxWidth().height(STRIP_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rest, key = { it.key }) { view ->
                            small(view, Modifier.height(STRIP_HEIGHT).aspectRatio(STRIP_RATIO))
                        }
                    }
                }
            }
        }
    }
}

private val STRIP_HEIGHT = 132.dp
private const val STRIP_RATIO = 0.8f

/** The strip beside the spotlight, in landscape: its tiles a little wider than tall. */
private val STRIP_WIDTH = 160.dp
private const val SIDE_RATIO = 1.25f

/** What's on screen: spotlight (pinned, full screen, or chosen), else the DM or group layout. */
@Composable
internal fun Stage(
    ui: CallUi,
    views: List<CallView>,
    layout: StageLayout,
    full: Boolean,
) {
    when {
        full || layout.pinned != null || (layout.spotlightOn(views) && views.size > 1) -> Spotlight(views, layout)
        ui.isDirect && ui.anyVideo -> DirectVideo(ui)
        ui.isDirect -> DirectAudio(ui)
        else -> GroupGrid(views, layout)
    }
}

/** Grid ↔ spotlight is offered in groups, or a DM once there's video or a shared screen. */
internal fun offersLayouts(
    ui: CallUi,
    views: List<CallView>,
) = views.size > 1 && (!ui.isDirect || ui.anyVideo || views.any { it.screen })

/** Everyone as tiles (and shared screens): video where there is some, avatars otherwise. Tap one to focus it. */
@Composable
internal fun GroupGrid(
    views: List<CallView>,
    layout: StageLayout,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val count = views.size.coerceAtLeast(1)
        val landscape = maxWidth > maxHeight
        val columns =
            when {
                count <= 1 -> {
                    1
                }

                // Landscape: side by side, wider than tall, so a row fits the screen's height.
                landscape -> {
                    if (count <= 2) {
                        2
                    } else if (count <= LANDSCAPE_THREE) {
                        3
                    } else {
                        4
                    }
                }

                count <= 4 || maxWidth < 600.dp -> {
                    2
                }

                else -> {
                    3
                }
            }
        val ratio =
            when {
                landscape -> LANDSCAPE_RATIO
                columns == 1 -> 0.75f
                else -> 1f
            }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(views, key = { it.key }) { view ->
                Tile(
                    view.tile,
                    Modifier.fillMaxWidth().aspectRatio(ratio),
                    screen = view.screen,
                    onClick = { layout.pinned = view.key },
                )
            }
        }
    }
}

/** Up to this many share three columns in landscape; more take four. */
private const val LANDSCAPE_THREE = 6
private const val LANDSCAPE_RATIO = 4f / 3f
