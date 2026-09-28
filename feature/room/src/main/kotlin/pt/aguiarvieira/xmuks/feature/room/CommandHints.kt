package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser

/**
 * Slash commands as they're typed: while the command name is being written, the matching ones
 * (gomuks' built-ins, its text prefixes, the room's bots) to pick from; once one is chosen, its
 * usage and what each parameter means.
 */
@Composable
internal fun CommandHints(
    draft: TextFieldState,
    commands: List<BotCommand>,
    modifier: Modifier = Modifier,
) {
    val text by remember(draft) { derivedStateOf { draft.text.toString() } }
    if (!text.startsWith("/") || text.startsWith("//") || commands.isEmpty()) return
    val chosen =
        remember(text, commands) {
            CommandParser.match(text, commands)?.takeIf {
                text.length >
                    it.command.length + 1
            }
        }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        if (chosen != null) {
            Usage(chosen)
        } else {
            val typed = text.removePrefix("/").lowercase()
            val matches =
                remember(typed, commands) {
                    commands
                        .filter { cmd -> (listOf(cmd.command) + cmd.aliases).any { it.lowercase().startsWith(typed) } }
                        .sortedWith(compareBy({ it.source != BotCommand.GOMUKS }, { it.command }))
                }
            if (matches.isEmpty()) return@Column
            LazyColumn(Modifier.heightIn(max = SUGGESTIONS_MAX)) {
                items(matches, key = { it.source + "/" + it.command }) { cmd ->
                    Suggestion(cmd) { draft.setTextAndPlaceCursorAtEnd("/${cmd.command} ") }
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Suggestion(
    command: BotCommand,
    onPick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 6.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(command.usage) }
                if (command.source != BotCommand.GOMUKS) {
                    withStyle(SpanStyle(color = colors.primary)) { append("  " + command.source) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        command.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** `/kick <user_id> [reason]`, then one line per parameter. */
@Composable
private fun Usage(command: BotCommand) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            command.usage,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = colors.primary
        )
        command.parameters.forEach { param ->
            val description = param.description ?: return@forEach
            Text(
                "${param.key}: $description",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SUGGESTIONS_MAX = 220.dp
