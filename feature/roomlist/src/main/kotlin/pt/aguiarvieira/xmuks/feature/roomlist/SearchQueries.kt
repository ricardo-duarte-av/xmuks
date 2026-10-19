package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Normalizer

/** The search queries of the three tabs; each tab keeps its own. */
class SearchQueries(
    val chats: TextFieldState = TextFieldState(),
    val dms: TextFieldState = TextFieldState(),
    val spaces: TextFieldState = TextFieldState(),
)

/**
 * The home tabs' bar: the filter field, then mentions, message search and options. Tapping the
 * field grows it over the whole bar, pushing the three out; back (or closing the keyboard) brings
 * them back, the filter kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterBar(
    state: TextFieldState,
    placeholder: String,
    onMentions: () -> Unit,
    onSearch: () -> Unit,
    onOptions: () -> Unit,
    /** The list has scrolled up under the bar: it takes a tone of its own, so the edge reads. */
    scrolledUnder: Boolean = false,
) {
    var typing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val ime = WindowInsets.isImeVisible
    // The keyboard went away (back, or its Search key): done typing.
    LaunchedEffect(ime) { if (!ime) focus.clearFocus() }
    BackHandler(enabled = typing) { focus.clearFocus() }
    val end by animateDpAsState(if (typing) 16.dp else 4.dp, label = "filterEnd")
    // Scrolled under: a tone of its own and a hairline beneath, as an app bar takes when content
    // scrolls under it, so the bar's edge reads in both themes.
    val colors = MaterialTheme.colorScheme
    val tone by animateColorAsState(
        if (scrolledUnder) colors.surfaceContainerLow else Color.Transparent,
        label = "filterTone"
    )
    val line by animateColorAsState(
        if (scrolledUnder) colors.outlineVariant else Color.Transparent,
        label = "filterLine"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .drawBehind {
                    drawRect(tone)
                    val stroke = 1.dp.toPx()
                    drawRect(line, Offset(0f, size.height - stroke), Size(size.width, stroke))
                }.padding(bottom = 4.dp),
    ) {
        SearchField(
            state,
            placeholder,
            Modifier.weight(1f).onFocusChanged { typing = it.isFocused },
            icon = R.drawable.ic_filter,
            padding = PaddingValues(start = 16.dp, end = end, top = 4.dp, bottom = 4.dp),
        )
        AnimatedVisibility(
            visible = !typing,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Row(Modifier.padding(end = 8.dp)) {
                listOf(
                    Triple(R.drawable.ic_mention, R.string.account_mentions, onMentions),
                    Triple(R.drawable.ic_search, R.string.account_search, onSearch),
                    Triple(R.drawable.ic_settings, R.string.account_options, onOptions),
                ).forEach { (icon, label, action) ->
                    IconButton(onClick = action) { Icon(painterResource(icon), stringResource(label)) }
                }
            }
        }
    }
}

/** A pill-shaped filter field under the app bar. */
@Composable
fun SearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_search,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    val focus = LocalFocusManager.current
    TextField(
        state = state,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear)
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { focus.clearFocus() },
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        modifier = modifier.fillMaxWidth().padding(padding),
    )
}

/**
 * Keeps only the items whose [name] contains the query, ignoring case and accents ("joao" finds
 * "João"). Runs off the main thread; a blank query passes the list through untouched.
 */
internal fun <T> Flow<List<T>>.filteredBy(
    query: TextFieldState,
    name: (T) -> String,
): Flow<List<T>> {
    val normalizedQuery = snapshotFlow { query.text.toString() }.map { it.trim().folded() }.distinctUntilChanged()
    return combine(this, normalizedQuery) { items, q ->
        if (q.isEmpty()) items else items.filter { name(it).folded().contains(q) }
    }.flowOn(Dispatchers.Default)
}

private val combiningMarks = Regex("\\p{Mn}+")

/** Lowercase, accents removed. */
internal fun String.folded(): String =
    combiningMarks.replace(Normalizer.normalize(lowercase(), Normalizer.Form.NFD), "")
