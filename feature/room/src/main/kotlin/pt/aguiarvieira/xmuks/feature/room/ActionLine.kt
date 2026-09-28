package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * A line for things people *do* rather than say — /me emotes and state changes (joins, renames,
 * kicks…): left-aligned for everyone, no bubble, a small avatar, the actor's coloured name running
 * into the text and the time at the end of its last line. They're events like any other, so they
 * carry reactions (and later replies).
 */
@Composable
private fun ActionLine(
    avatarName: String,
    avatarId: String,
    avatarUrl: String?,
    reactions: List<Reaction>,
    resolver: MediaResolver,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable (LastLine) -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = 6.dp, bottom = 2.dp)) {
        Row {
            // Centred on the first line of text.
            Box(Modifier.padding(top = AVATAR_NUDGE)) { RoomAvatar(avatarName, avatarId, avatarUrl, size = AVATAR) }
            Spacer(Modifier.width(GAP))
            val lastLine = remember { LastLine() }
            ContentWithFooter(lastLine, footer = footer) { text(lastLine) }
        }
        if (reactions.isNotEmpty()) Reactions(reactions, resolver, Modifier.padding(start = AVATAR + GAP, top = 4.dp))
    }
}

/** A /me message: "* Name does something", fully formatted (bold, links, emoji…), never forced italic. */
@Composable
internal fun EmoteRow(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val content = message.content as MessageContent.Text
    val color = MaterialTheme.colorScheme.onSurface
    ActionLine(
        avatarName = message.label.shownName,
        avatarId = message.label.profileId ?: message.sender,
        avatarUrl = resolver.avatar(message.senderAvatarMxc),
        reactions = message.reactions,
        resolver = resolver,
        footer = { Footer(message, MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
    ) { lastLine ->
        val name = senderText(message.label)
        val prefix = remember(name) { AnnotatedString("* ") + name + AnnotatedString(" ") }
        val style = MaterialTheme.typography.bodyLarge
        val tint = if (content.kind == TextKind.Notice) color.copy(alpha = NOTICE) else color
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            message.reply?.let { Reply(it, color) }
            val html = content.html
            if (html != null) {
                HtmlContent(html, tint, style, { resolver.media(it, false) }, lastLine = lastLine, prefix = prefix)
            } else {
                PlainContent(content.body, tint, style, lastLine = lastLine, prefix = prefix)
            }
        }
    }
}

/** Joins, leaves, kicks, renames, room settings… with the actor's name in their colour. */
@Composable
internal fun StateChangeRow(
    item: TimelineItem.StateChange,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val time = rememberTime(item.timestamp)
    ActionLine(
        avatarName = item.actorName,
        avatarId = item.actor,
        avatarUrl = resolver.avatar(item.actorAvatarMxc),
        reactions = item.reactions,
        resolver = resolver,
        footer = { Text(time, color = muted, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
    ) { lastLine ->
        val template = changeTemplate(item.change)
        val actorColor = senderColor(item.actor)
        // A rename is told from the old name: "Ann is now Annie".
        val actorName = (item.change as? Change.Renamed)?.from ?: item.actorName
        val text =
            remember(template, actorName, actorColor) {
                buildAnnotatedString {
                    val parts = template.split(ACTOR)
                    parts.forEachIndexed { index, part ->
                        append(part)
                        if (index < parts.lastIndex) {
                            withStyle(
                                SpanStyle(color = actorColor, fontWeight = FontWeight.Medium)
                            ) { append(actorName) }
                        }
                    }
                }
            }
        Text(
            text,
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
            onTextLayout = { lastLine.update(it) },
        )
    }
}

/** The change's sentence with [ACTOR] where the actor's (coloured) name goes. */
@Composable
private fun changeTemplate(change: Change): String =
    when (change) {
        Change.Joined -> stringResource(R.string.change_joined, ACTOR)
        Change.Left -> stringResource(R.string.change_left, ACTOR)
        is Change.Invited -> stringResource(R.string.change_invited, ACTOR, change.target)
        is Change.Kicked -> stringResource(R.string.change_kicked, ACTOR, change.target)
        is Change.Banned -> stringResource(R.string.change_banned, ACTOR, change.target)
        is Change.Renamed -> stringResource(R.string.change_renamed, ACTOR, change.to.orEmpty())
        Change.ChangedAvatar -> stringResource(R.string.change_avatar, ACTOR)
        is Change.RoomName -> stringResource(R.string.change_room_name, ACTOR, change.name.orEmpty())
        is Change.RoomTopic -> stringResource(R.string.change_room_topic, ACTOR)
        Change.RoomAvatar -> stringResource(R.string.change_room_avatar, ACTOR)
        Change.RoomCreated -> stringResource(R.string.change_created, ACTOR)
        Change.EncryptionEnabled -> stringResource(R.string.change_encryption, ACTOR)
    }

/** Stands in for the actor's name in a translated sentence, wherever the language puts it. */
private const val ACTOR = "\u0001"
private val AVATAR = 20.dp
private val AVATAR_NUDGE = 2.dp
private val GAP = 8.dp
private val EDGE = 12.dp
private const val NOTICE = 0.75f
