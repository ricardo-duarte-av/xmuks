package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Writing a poll: a question, two or more answers, one choice or several, results open or hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollComposer(
    onStart: (PollDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    val filled = answers.map { it.trim() }.filter { it.isNotEmpty() }
    val ready = question.isNotBlank() && filled.size >= 2
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.poll_new), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                question,
                { question = it },
                label = { Text(stringResource(R.string.poll_question)) },
                modifier = Modifier.fillMaxWidth(),
            )
            answers.forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text,
                        { answers[i] = it },
                        label = { Text(stringResource(R.string.poll_answer, i + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (answers.size > 2) {
                        IconButton(onClick = { answers.removeAt(i) }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.poll_remove_answer))
                        }
                    }
                }
            }
            if (answers.size < MAX_ANSWERS) {
                TextButton(onClick = { answers.add("") }) { Text(stringResource(R.string.poll_add_answer)) }
            }
            SwitchRow(stringResource(R.string.poll_multiple), multiple) { multiple = it }
            SwitchRow(stringResource(R.string.poll_hide_results), hidden) { hidden = it }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = ready,
                    onClick = {
                        onStart(PollDraft(question.trim(), filled, if (multiple) filled.size else 1, !hidden))
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.poll_start)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

private const val MAX_ANSWERS = 20
