package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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

/** A pill-shaped filter field under the app bar. */
@Composable
fun SearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    TextField(
        state = state,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
