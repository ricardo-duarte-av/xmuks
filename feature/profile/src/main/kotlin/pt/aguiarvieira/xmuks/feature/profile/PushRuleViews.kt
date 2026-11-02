package pt.aguiarvieira.xmuks.feature.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.push.NotifyPreset
import pt.aguiarvieira.xmuks.core.data.push.PushRule
import pt.aguiarvieira.xmuks.core.data.push.PushRuleKind
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** A rule: who or what it's about, its switch, what it does (a preset each), and its raw JSON or deletion. */
@Composable
internal fun RuleCard(
    rule: PushRule,
    targets: RuleTargets,
    edits: PushRuleEdits,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleIdentity(rule, targets, Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = { edits.setEnabled(rule, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PresetChips(rule.preset, { edits.setPreset(rule, it) }, Modifier.weight(1f))
            IconButton(onClick = { editing = true }) {
                Icon(painterResource(R.drawable.ic_code), stringResource(R.string.push_rule_edit_raw))
            }
            // The server's own rules can't be deleted: dimmed, and a tap says why.
            IconButton(
                onClick = {
                    if (rule.default) {
                        Toast.makeText(context, R.string.push_rule_default_kept, Toast.LENGTH_SHORT).show()
                    } else {
                        deleting = true
                    }
                },
                modifier = if (rule.default) Modifier.alpha(DISABLED_ALPHA) else Modifier,
            ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.push_rule_delete)) }
        }
        if (rule.preset == NotifyPreset.Custom) {
            Text(
                stringResource(R.string.push_rule_custom),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) RawRuleDialog(rule, { edits.save(rule, it) }) { editing = false }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.push_rule_delete)) },
            text = { Text(stringResource(R.string.push_rule_delete_confirm, ruleTitle(rule))) },
            confirmButton = {
                TextButton(onClick = {
                    edits.delete(rule)
                    deleting = false
                }) { Text(stringResource(R.string.push_rule_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A room's avatar and name, a person's, or the rule's title and what it matches. */
@Composable
private fun RuleIdentity(
    rule: PushRule,
    targets: RuleTargets,
    modifier: Modifier = Modifier,
) {
    val roomId = rule.roomId
    val userId = rule.userId
    val need by rememberUpdatedState(targets.needProfile)
    if (userId != null) LaunchedEffect(userId) { need(userId) }
    val target =
        when {
            roomId != null -> {
                targets.rooms[roomId] ?: RuleTarget(roomId, null, null)
            }

            userId != null -> {
                targets.people[userId]?.let { it.copy(avatar = targets.media.thumbnail(it.avatar)) }
                    ?: RuleTarget(userId, null, null)
            }

            else -> {
                null
            }
        }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (target != null) {
            RoomAvatar(target.name ?: target.id, target.id, target.avatar, size = 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    target.name ?: target.id,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (rule.kind == PushRuleKind.Room ||
                        rule.kind == PushRuleKind.Sender
                    ) {
                        target.id
                    } else {
                        ruleSummary(rule)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column {
                Text(
                    ruleTitle(rule),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    ruleSummary(rule).ifEmpty { stringResource(R.string.push_rule_always) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The presets to pick from, [current] chosen ([NotifyPreset.Custom] chooses none). */
@Composable
internal fun PresetChips(
    current: NotifyPreset,
    onChoose: (NotifyPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotifyPreset.choices.forEach { preset ->
            FilterChip(
                selected = preset == current,
                onClick = { onChoose(preset) },
                label = { Text(stringResource(presetName(preset))) },
            )
        }
    }
}

/** The rule as JSON, to change by hand: only the actions take for the server's own rules. */
@Composable
private fun RawRuleDialog(
    rule: PushRule,
    onSave: (JsonObject) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = remember(rule) { TextFieldState(PRETTY.encodeToString(JsonObject.serializer(), rule.editable())) }
    var invalid by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_rule_edit_raw)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (rule.default) R.string.push_rule_raw_default else R.string.push_rule_raw_own),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                JsonField(text, Modifier.heightIn(min = 200.dp, max = 360.dp))
                invalid?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { GomuksJson.parseToJsonElement(text.text.toString()) as JsonObject }
                    .onSuccess {
                        onSave(it)
                        onDismiss()
                    }.onFailure { invalid = it.message ?: it.javaClass.simpleName }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * A new rule of [kind]: a content rule's pattern (its ID too), a room's or person's ID, or an
 * override's or underride's ID and conditions; and what it does.
 */
@Composable
internal fun AddRuleDialog(
    kind: PushRuleKind,
    onAdd: (ruleId: String, content: JsonObject) -> Unit,
    onDismiss: () -> Unit,
) {
    val id = rememberTextFieldState()
    val conditions =
        rememberTextFieldState(
            "[\n  {\"kind\": \"event_match\", \"key\": \"room_id\", \"pattern\": \"!room:server\"}\n]"
        )
    var preset by rememberSaveable { mutableStateOf(NotifyPreset.Notify) }
    var invalid by remember { mutableStateOf<Int?>(null) }
    val withConditions = kind == PushRuleKind.Override || kind == PushRuleKind.Underride
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_rule_add_kind, stringResource(kindName(kind)))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    state = id,
                    label = { Text(stringResource(idLabel(kind))) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withConditions) {
                    Text(stringResource(R.string.push_rule_conditions), style = MaterialTheme.typography.labelMedium)
                    JsonField(conditions, Modifier.heightIn(min = 120.dp, max = 220.dp))
                }
                Text(stringResource(R.string.push_rule_does), style = MaterialTheme.typography.labelMedium)
                PresetChips(preset, { preset = it })
                invalid?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ruleId = id.text.toString().trim()
                val parsed =
                    if (withConditions) {
                        runCatching {
                            GomuksJson.parseToJsonElement(conditions.text.toString()) as JsonArray
                        }.getOrNull()
                    } else {
                        null
                    }
                invalid =
                    when {
                        ruleId.isEmpty() -> R.string.push_rule_id_needed
                        ruleId.startsWith(".") -> R.string.push_rule_id_reserved
                        kind == PushRuleKind.Room && !ruleId.startsWith("!") -> R.string.push_rule_room_needed
                        kind == PushRuleKind.Sender && !ruleId.startsWith("@") -> R.string.push_rule_user_needed
                        withConditions && parsed == null -> R.string.push_rule_conditions_invalid
                        else -> null
                    }
                if (invalid != null) return@TextButton
                val content =
                    buildJsonObject {
                        put("actions", preset.actions())
                        parsed?.let { put("conditions", it) }
                        if (kind == PushRuleKind.Content) put("pattern", JsonPrimitive(ruleId))
                    }
                onAdd(ruleId, content)
                onDismiss()
            }) { Text(stringResource(R.string.push_rule_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun JsonField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * What a rule is called: a keyword's word, the server's rule IDs tidied (".m.rule.suppress_notices"
 * → "Suppress notices"), or its ID.
 */
internal fun ruleTitle(rule: PushRule): String =
    when {
        rule.kind == PushRuleKind.Content && !rule.pattern.isNullOrBlank() -> {
            rule.pattern!!
        }

        rule.ruleId.startsWith(
            "."
        ) -> {
            rule.ruleId
                .substringAfterLast('.')
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }

        else -> {
            rule.ruleId
        }
    }

/** What a rule matches, briefly: a pattern, or its conditions ("content.msgtype ~ m.notice"); empty for always. */
internal fun ruleSummary(rule: PushRule): String =
    when (rule.kind) {
        PushRuleKind.Content -> {
            "content.body ~ ${rule.pattern.orEmpty()}"
        }

        PushRuleKind.Room, PushRuleKind.Sender -> {
            rule.ruleId
        }

        PushRuleKind.Override, PushRuleKind.Underride -> {
            rule.conditions.mapNotNull { it as? JsonObject }.joinToString(" · ", transform = ::conditionText)
        }
    }

private fun conditionText(condition: JsonObject): String {
    fun field(name: String) = (condition[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val value = condition["value"]?.toString().orEmpty()
    return when (val kind = field("kind")) {
        "event_match" -> "${field("key")} ~ ${field("pattern")}"
        "event_property_is" -> "${field("key")} = $value"
        "event_property_contains" -> "${field("key")} ∋ $value"
        "contains_display_name" -> "body ∋ display name"
        "room_member_count" -> "members ${field("is")}"
        "sender_notification_permission" -> "sender may @${field("key")}"
        else -> kind
    }
}

private fun presetName(preset: NotifyPreset) =
    when (preset) {
        NotifyPreset.Off -> R.string.preset_off
        NotifyPreset.Notify -> R.string.preset_notify
        NotifyPreset.NotifySound -> R.string.preset_sound
        NotifyPreset.Highlight -> R.string.preset_highlight
        NotifyPreset.Custom -> R.string.preset_custom
    }

private fun idLabel(kind: PushRuleKind) =
    when (kind) {
        PushRuleKind.Content -> R.string.push_rule_id_content
        PushRuleKind.Room -> R.string.push_rule_id_room
        PushRuleKind.Sender -> R.string.push_rule_id_sender
        else -> R.string.push_rule_id
    }

private val PRETTY = Json { prettyPrint = true }
