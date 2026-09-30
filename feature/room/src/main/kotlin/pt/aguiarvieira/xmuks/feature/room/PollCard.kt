package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.PollAnswer

/**
 * A poll in its bubble: the question, each answer to tap (a radio for one choice, boxes for
 * several), and — when the poll shows them, or it's over — the votes each has.
 */
@Composable
internal fun PollCard(
    content: MessageContent.Poll,
    color: Color,
    onVote: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tally = content.tally
    val poll = content.poll
    val showVotes = poll.disclosed || tally.ended
    Column(modifier.widthIn(min = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_poll), null, Modifier.size(20.dp), tint = color)
            Text(
                poll.question.ifBlank { stringResource(R.string.poll) },
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val most = tally.votes.values.maxOrNull() ?: 0
        poll.answers.forEach { answer ->
            AnswerRow(
                answer,
                picked = answer.id in tally.mine,
                single = poll.maxSelections == 1,
                votes = if (showVotes) tally.votes[answer.id] ?: 0 else null,
                share =
                    if (showVotes &&
                        tally.voters > 0
                    ) {
                        (tally.votes[answer.id] ?: 0).toFloat() / tally.voters
                    } else {
                        0f
                    },
                winning = tally.ended && most > 0 && tally.votes[answer.id] == most,
                enabled = !tally.ended,
                color = color,
                onTap = { onVote(picksAfter(content, answer.id)) },
            )
        }
        Text(footer(content), color = color.copy(alpha = QUIET), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun footer(content: MessageContent.Poll): String {
    val tally = content.tally
    val votes = pluralStringResource(R.plurals.poll_votes, tally.voters, tally.voters)
    return when {
        tally.ended -> stringResource(R.string.poll_ended, votes)
        !content.poll.disclosed -> stringResource(R.string.poll_results_at_end)
        content.poll.maxSelections > 1 -> stringResource(R.string.poll_pick_up_to, content.poll.maxSelections, votes)
        else -> votes
    }
}

@Composable
private fun AnswerRow(
    answer: PollAnswer,
    picked: Boolean,
    single: Boolean,
    votes: Int?,
    share: Float,
    winning: Boolean,
    enabled: Boolean,
    color: Color,
    onTap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .tapOrHold(enabled = enabled, onClick = onTap)
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (single) {
                RadioButton(
                    picked,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = color, unselectedColor = color),
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                Checkbox(
                    picked,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                answer.text,
                color = color,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (winning) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
            if (votes != null) {
                Text(
                    votes.toString(),
                    color = color,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                )
            }
        }
        if (votes != null) VoteBar(share, color)
    }
}

@Composable
private fun VoteBar(
    share: Float,
    color: Color,
) {
    Box(
        Modifier
            .padding(start = 40.dp, end = 4.dp, bottom = 2.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = TRACK)),
    ) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

private const val QUIET = 0.7f
private const val TRACK = 0.2f
