package pt.aguiarvieira.xmuks.feature.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.xmuks.core.data.push.Keyword
import pt.aguiarvieira.xmuks.core.data.push.PushRuleset
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenBody
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader

@Composable
fun KeywordsRoute(
    roomId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PushRulesViewModel = hiltViewModel(key = "keywords:$roomId"),
) {
    val ruleset by viewModel.ruleset.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val error by viewModel.tasks.error.collectAsStateWithLifecycle()
    val edits =
        remember(viewModel, roomId) {
            KeywordEdits(
                add = viewModel::addKeyword,
                remove = viewModel::removeKeyword,
                setEnabled = viewModel::setKeywordEnabled,
                addHere = { word -> roomId?.let { viewModel.addRoomKeyword(it, word) } },
                removeHere = { word -> roomId?.let { viewModel.removeRoomKeyword(it, word) } },
                setHere = { keyword, on -> roomId?.let { viewModel.setKeywordHere(it, keyword, on) } },
            )
        }
    KeywordsScreen(
        ruleset = ruleset,
        roomId = roomId,
        roomName = roomId?.let { rooms[it]?.name ?: it },
        edits = edits,
        busy = busy,
        error = error,
        onErrorShow = viewModel.tasks::errorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Keywords: words that notify and highlight. The account's ([roomId] null), each switchable and
 * removable; or one room's: words of its own, and the account's with whether each notifies there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordsScreen(
    ruleset: PushRuleset?,
    roomId: String?,
    roomName: String?,
    edits: KeywordEdits,
    busy: Boolean,
    error: String?,
    onErrorShow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val failed = error?.let { stringResource(R.string.action_failed, it) }
    val errorShown by rememberUpdatedState(onErrorShow)
    LaunchedEffect(failed) {
        if (failed != null) {
            errorShown()
            snackbar.showSnackbar(failed)
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = ScreenCards.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenHeader {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = {
                            Column {
                                Text(stringResource(R.string.keywords))
                                roomName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                    )
                    AddKeyword(if (roomId == null) edits.add else edits.addHere)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
    ) { padding ->
        ScreenBody(
            Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            when {
                ruleset == null -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                roomId == null -> {
                    AccountKeywords(ruleset.keywords, edits)
                }

                else -> {
                    RoomKeywordList(ruleset, roomId, edits)
                }
            }
        }
    }
}

/** A box for a new word, and the button that adds it. */
@Composable
private fun AddKeyword(onAdd: (String) -> Unit) {
    val word = rememberTextFieldState()
    val add = {
        val text = word.text.toString().trim()
        if (text.isNotEmpty()) {
            onAdd(text)
            word.clearText()
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            state = word,
            placeholder = { Text(stringResource(R.string.keyword_hint)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            onKeyboardAction = { add() },
            shape = CircleShape,
            colors = ScreenCards.pillColors,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = add
        ) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.keyword_add)) }
    }
}

@Composable
private fun AccountKeywords(
    keywords: List<Keyword>,
    edits: KeywordEdits,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "intro") { Note(R.string.keywords_intro) }
        if (keywords.isEmpty()) item(key = "none") { Note(R.string.keywords_none) }
        items(keywords, key = { it.ruleId }) { keyword ->
            ListItem(
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = keyword.enabled, onCheckedChange = { edits.setEnabled(keyword, it) })
                        IconButton(onClick = { edits.remove(keyword) }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.keyword_remove))
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(keyword.word, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun RoomKeywordList(
    ruleset: PushRuleset,
    roomId: String,
    edits: KeywordEdits,
) {
    val keywords = remember(ruleset, roomId) { ruleset.roomKeywords(roomId) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "own-title") { GroupTitle(R.string.keywords_here) }
        item(key = "own-intro") { Note(R.string.keywords_here_intro) }
        items(keywords.own, key = { "own:$it" }) { word ->
            ListItem(
                trailingContent = {
                    IconButton(onClick = { edits.removeHere(word) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.keyword_remove))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        item(key = "global-title") { GroupTitle(R.string.keywords_yours) }
        item(key = "global-intro") { Note(R.string.keywords_yours_intro) }
        if (keywords.global.isEmpty()) item(key = "global-none") { Note(R.string.keywords_none) }
        items(keywords.global, key = { "global:${it.first.ruleId}" }) { (keyword, silenced) ->
            // Off everywhere: dimmed, and its switch says where to turn it on.
            ListItem(
                supportingContent =
                    if (keyword.enabled) {
                        null
                    } else {
                        (
                            {
                                Text(
                                    stringResource(R.string.keyword_off_everywhere)
                                )
                            }
                        )
                    },
                trailingContent = {
                    Switch(
                        checked = keyword.enabled && !silenced,
                        onCheckedChange = { on ->
                            if (keyword.enabled) {
                                edits.setHere(keyword, on)
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.keyword_off_everywhere_toast,
                                        Toast.LENGTH_SHORT
                                    ).show()
                            }
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = if (keyword.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
            ) { Text(keyword.word, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
