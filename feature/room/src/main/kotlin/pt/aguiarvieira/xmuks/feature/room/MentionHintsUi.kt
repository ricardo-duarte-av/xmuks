package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTarget
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** Mentions (or emoji) the draft could complete to, and picking one. */
class MentionHintsUi(
    val items: List<MentionTarget> = emptyList(),
    val avatar: (String?) -> String? = { null },
    val onPick: (MentionTarget) -> Unit = {},
    val emoji: List<EmojiHint> = emptyList(),
    val onPickEmoji: (EmojiHint) -> Unit = {},
)

/** Above a message or caption box while a mention or `:shortcode` is typed: who, which room or emoji it could be. */
@Composable
fun MentionHints(
    hints: MentionHintsUi,
    modifier: Modifier = Modifier,
) {
    if (hints.items.isEmpty() && hints.emoji.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
        LazyColumn(Modifier.heightIn(max = SUGGESTIONS_HEIGHT)) {
            items(hints.emoji, key = { "emoji/" + it.picked.key + "/" + it.shortcode }) { hint ->
                EmojiHintRow(hint, hints.avatar) { hints.onPickEmoji(hint) }
            }
            items(hints.items, key = { it.id }) { target ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            hints.onPick(
                                target
                            )
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoomAvatar(target.name, target.id, hints.avatar(target.avatarMxc), size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            target.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            target.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

/** One emoji: itself, its `:shortcode:` and (for a Unicode one) its name. */
@Composable
private fun EmojiHintRow(
    hint: EmojiHint,
    avatar: (String?) -> String?,
    onPick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (val picked = hint.picked) {
                is Picked.Unicode -> Text(picked.emoji, fontSize = 24.sp)
                is Picked.Custom -> AsyncImage(avatar(picked.image.mxc), picked.image.shortcode, Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                ":${hint.shortcode}:",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            hint.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val SUGGESTIONS_HEIGHT = 220.dp
