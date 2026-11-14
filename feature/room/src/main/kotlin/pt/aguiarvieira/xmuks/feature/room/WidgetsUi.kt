package pt.aguiarvieira.xmuks.feature.room

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import pt.aguiarvieira.xmuks.core.data.timeline.RoomWidget
import java.net.URI
import java.util.Locale

/** The room's widgets, by name; tapping one opens its page in place of the timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetsSheet(
    widgets: List<RoomWidget>,
    onOpen: (RoomWidget) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.widgets),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(widgets, key = { it.widget.id }) { item ->
                val widget = item.widget
                ListItem(
                    supportingContent = {
                        Text(
                            widget.title?.takeIf { it != widget.name } ?: hostOf(widget.template),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_widgets), null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.tapOrHold { onOpen(item) },
                ) { Text(widget.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * A widget's page where the timeline was, under a bar with the way back to the room, reloading and
 * opening it in the browser. The page is a plain web page: no widget API is offered to it.
 */
@Composable
internal fun WidgetPage(
    widget: RoomWidget,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    val url =
        remember(widget, dark) {
            widget.url(
                mapOf(
                    "org.matrix.msc2873.client_id" to context.packageName,
                    "org.matrix.msc2873.client_theme" to if (dark) "dark" else "light",
                    "org.matrix.msc2873.client_language" to Locale.getDefault().toLanguageTag(),
                ),
            )
        }
    var progress by remember(url) { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<WebView?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back_to_room))
            }
            Text(
                widget.widget.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { view?.reload() }) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.reload_widget))
            }
            IconButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            }) {
                Icon(painterResource(R.drawable.ic_open_in_new), stringResource(R.string.open_in_browser))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            if (progress in 0 until FULL) {
                LinearProgressIndicator(
                    progress = { progress / FULL.toFloat() },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
        // A new page for each widget (and address): nothing of the last one carries over.
        key(url) {
            AndroidView(
                factory = { ctx ->
                    widgetWebView(ctx, onProgress = { progress = it }).also {
                        view = it
                        it.loadUrl(url)
                    }
                },
                onRelease = {
                    if (view === it) view = null
                    it.stopLoading()
                    it.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun widgetWebView(
    context: Context,
    onProgress: (Int) -> Unit,
): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Links stay in the page's place; the bar's button opens it in the browser.
        webViewClient =
            object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: Bitmap?,
                ) = onProgress(0)

                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) = onProgress(FULL)
            }
        webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView,
                    newProgress: Int,
                ) = onProgress(newProgress)
            }
    }

private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url

private const val FULL = 100
private const val HALF = 0.5f

/**
 * The room's widgets as its screen shows them: whether their list is up, and which one's page
 * is open in place of the timeline (by ID, kept across the activity being recreated).
 */
@Stable
internal class WidgetsState(
    private val widgets: List<RoomWidget>,
    private val openId: MutableState<String?>,
    private val listShown: MutableState<Boolean>,
) {
    val open: RoomWidget? get() = openId.value?.let { id -> widgets.firstOrNull { it.widget.id == id } }

    val showingList: Boolean get() = listShown.value

    val all: List<RoomWidget> get() = widgets

    fun showList() {
        listShown.value = true
    }

    fun hideList() {
        listShown.value = false
    }

    fun open(widget: RoomWidget) {
        listShown.value = false
        openId.value = widget.widget.id
    }

    fun close() {
        openId.value = null
    }

    /** [count], or none while a widget's page is open. */
    fun unlessOpen(count: Int): Int = if (open == null) count else 0
}

@Composable
internal fun rememberWidgetsState(widgets: List<RoomWidget>): WidgetsState {
    val openId = rememberSaveable { mutableStateOf<String?>(null) }
    val listShown = remember { mutableStateOf(false) }
    return remember(widgets) { WidgetsState(widgets, openId, listShown) }
}

/** The widgets' list when up; back from a widget's page returns to the room. */
@Composable
internal fun WidgetsOverlays(state: WidgetsState) {
    BackHandler(enabled = state.open != null, onBack = state::close)
    if (state.showingList) WidgetsSheet(state.all, state::open, state::hideList)
}

/** [content], or [instead] with the open widget while one's page is open. */
@Composable
internal fun UnlessWidget(
    state: WidgetsState,
    instead: @Composable (RoomWidget) -> Unit,
    content: @Composable () -> Unit,
) {
    val open = state.open
    if (open != null) instead(open) else content()
}
