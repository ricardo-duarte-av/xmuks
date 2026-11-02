package pt.aguiarvieira.xmuks.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.data.push.Keyword
import pt.aguiarvieira.xmuks.core.data.push.NotifyPreset
import pt.aguiarvieira.xmuks.core.data.push.PushRule
import pt.aguiarvieira.xmuks.core.data.push.PushRuleKind
import pt.aguiarvieira.xmuks.core.data.push.PushRuleset
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenBody
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenHeader

/** Everything the push rule editor can change. */
@Immutable
class PushRuleEdits(
    val setEnabled: (PushRule, Boolean) -> Unit = { _, _ -> },
    val setPreset: (PushRule, NotifyPreset) -> Unit = { _, _ -> },
    /** The raw editor's JSON for a rule. */
    val save: (PushRule, JsonObject) -> Unit = { _, _ -> },
    val add: (PushRuleKind, ruleId: String, content: JsonObject) -> Unit = { _, _, _ -> },
    val delete: (PushRule) -> Unit = {},
)

/** What the keyword screen can change: the account's keywords, and one room's. */
@Immutable
class KeywordEdits(
    val add: (String) -> Unit = {},
    val remove: (Keyword) -> Unit = {},
    val setEnabled: (Keyword, Boolean) -> Unit = { _, _ -> },
    /** One room's own: added, taken away. */
    val addHere: (String) -> Unit = {},
    val removeHere: (String) -> Unit = {},
    /** One of the account's keywords notifying in the room, or silenced there. */
    val setHere: (Keyword, Boolean) -> Unit = { _, _ -> },
)

/** The names and avatars rules are shown with: rooms by ID, people by ID, and a person coming on screen. */
@Immutable
class RuleTargets(
    val rooms: Map<String, RuleTarget> = emptyMap(),
    val people: Map<String, RuleTarget> = emptyMap(),
    val media: ProfileMedia = ProfileMedia({ it }, { it }),
    val needProfile: (String) -> Unit = {},
)

@Composable
fun PushRulesRoute(
    onOpenKeywords: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PushRulesViewModel = hiltViewModel(key = "push-rules"),
) {
    val ruleset by viewModel.ruleset.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val busy by viewModel.tasks.busy.collectAsStateWithLifecycle()
    val error by viewModel.tasks.error.collectAsStateWithLifecycle()
    val edits =
        remember(viewModel) {
            PushRuleEdits(
                viewModel::setEnabled,
                viewModel::setPreset,
                viewModel::save,
                viewModel::add,
                viewModel::delete
            )
        }
    val media = remember(viewModel) { ProfileMedia(viewModel.media::avatar, viewModel.media::full) }
    PushRulesScreen(
        ruleset = ruleset,
        targets = RuleTargets(rooms, people, media, viewModel::needProfile),
        edits = edits,
        busy = busy,
        error = error,
        onErrorShow = viewModel.tasks::errorShown,
        onOpenKeywords = onOpenKeywords,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The account's push rules: keywords first (the usual reason to be here), then each of the five
 * kinds in the order they're checked; picking a kind lists its rules, to switch, re-action, edit,
 * add or delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushRulesScreen(
    ruleset: PushRuleset?,
    targets: RuleTargets,
    edits: PushRuleEdits,
    busy: Boolean,
    error: String?,
    onErrorShow: () -> Unit,
    onOpenKeywords: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The kind open at first; null for the sections. */
    initialKind: PushRuleKind? = null,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val query = rememberTextFieldState()
    BackHandler(enabled = kind != null) { kind = null }
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
                            IconButton(onClick = { if (kind != null) kind = null else onBack() }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                            }
                        },
                        title = { Text(stringResource(kind?.let(::kindName) ?: R.string.push_rules)) },
                    )
                    if (kind != null) {
                        TextField(
                            state = query,
                            placeholder = { Text(stringResource(R.string.push_rules_search)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            shape = CircleShape,
                            colors = ScreenCards.pillColors,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        },
        floatingActionButton = {
            if (kind != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text(stringResource(R.string.push_rule_add)) },
                )
            }
        },
    ) { padding ->
        ScreenBody(
            Modifier
                .padding(padding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            val shown = kind
            when {
                ruleset == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                shown == null -> {
                    Sections(ruleset, onOpenKeywords) { kind = it }
                }

                else -> {
                    RuleList(shown, ruleset[shown], query.text.toString(), targets, edits)
                }
            }
        }
    }
    val addTo = kind
    if (adding && addTo != null) {
        AddRuleDialog(addTo, { id, content -> edits.add(addTo, id, content) }) { adding = false }
    }
}

@Composable
private fun Sections(
    ruleset: PushRuleset,
    onOpenKeywords: () -> Unit,
    onOpen: (PushRuleKind) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "intro") {
            Text(
                stringResource(R.string.push_rules_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "keywords") {
            SectionRow(R.string.keywords, R.string.keywords_summary, ruleset.keywords.size, onOpenKeywords)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        items(PushRuleKind.entries, key = { it.wire }) { kind ->
            SectionRow(kindName(kind), kindSummary(kind), ruleset[kind].size) { onOpen(kind) }
        }
    }
}

@Composable
private fun SectionRow(
    title: Int,
    summary: Int,
    count: Int,
    onClick: () -> Unit,
) {
    ListItem(
        supportingContent = {
            Column {
                Text(stringResource(summary))
                Text(
                    pluralStringResource(R.plurals.push_rule_count, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(title)) }
}

/** One kind's rules matching [query]: those about a room or a person first, then the rest. */
@Composable
private fun RuleList(
    kind: PushRuleKind,
    rules: List<PushRule>,
    query: String,
    targets: RuleTargets,
    edits: PushRuleEdits,
) {
    val needle = query.trim()
    val shown =
        if (needle.isEmpty()) {
            rules
        } else {
            rules.filter { rule -> ruleTexts(rule, targets).any { it.contains(needle, ignoreCase = true) } }
        }
    val (targeted, general) = shown.partition { it.roomId != null || it.userId != null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(if (needle.isEmpty()) R.string.push_rules_none else R.string.push_rules_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        val grouped = targeted.isNotEmpty() && general.isNotEmpty()
        val groups = listOf(R.string.push_rules_targeted to targeted, R.string.push_rules_general to general)
        groups.forEach { (title, group) ->
            if (grouped) item(key = "header-$title") { GroupTitle(title) }
            items(group, key = { "${kind.wire}:${it.ruleId}" }) { rule ->
                RuleCard(rule, targets, edits)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
internal fun GroupTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** What a search can find a rule by: its ID, pattern, summary, and the room or person it's about. */
private fun ruleTexts(
    rule: PushRule,
    targets: RuleTargets,
): List<String> =
    listOfNotNull(
        rule.ruleId,
        rule.pattern,
        ruleSummary(rule),
        rule.roomId?.let { targets.rooms[it]?.name },
        rule.userId?.let { targets.people[it]?.name },
    )

internal fun kindName(kind: PushRuleKind) =
    when (kind) {
        PushRuleKind.Override -> R.string.push_kind_override
        PushRuleKind.Content -> R.string.push_kind_content
        PushRuleKind.Room -> R.string.push_kind_room
        PushRuleKind.Sender -> R.string.push_kind_sender
        PushRuleKind.Underride -> R.string.push_kind_underride
    }

private fun kindSummary(kind: PushRuleKind) =
    when (kind) {
        PushRuleKind.Override -> R.string.push_kind_override_summary
        PushRuleKind.Content -> R.string.push_kind_content_summary
        PushRuleKind.Room -> R.string.push_kind_room_summary
        PushRuleKind.Sender -> R.string.push_kind_sender_summary
        PushRuleKind.Underride -> R.string.push_kind_underride_summary
    }
