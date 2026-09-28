package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.Reader
import pt.aguiarvieira.xmuks.core.data.timeline.TextKind
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia
import pt.aguiarvieira.xmuks.core.designsystem.theme.senderColor

/**
 * A line for things people *do* rather than say — /me emotes and state changes (joins, renames,
 * kicks…): left-aligned for everyone, no bubble, a small avatar, the actor's coloured name running
 * into the text and the time at the end of its last line. They're events like any other, so they
 * carry reactions and read receipts (and later replies).
 */
@Composable
private fun ActionLine(
    avatar: Avatar,
    reactions: List<Reaction>,
    readBy: List<Reader>,
    resolver: MediaResolver,
    actions: TimelineActions,
    highlighted: Boolean,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable (LastLine) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .highlight(highlighted)
            .padding(start = EDGE, end = EDGE, top = 6.dp, bottom = 2.dp),
    ) {
        Row {
            // Centred on the first line of text.
            Box(Modifier.padding(top = AVATAR_NUDGE)) {
                RoomAvatar(
                    avatar.name,
                    avatar.id,
                    resolver.avatar(avatar.mxc),
                    size = AVATAR,
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            ).clickable { resolver.image(avatar.mxc, avatar.name)?.let(actions.openMedia) },
                )
            }
            Spacer(Modifier.width(GAP))
            val lastLine = remember { LastLine() }
            Box(Modifier.weight(1f)) { ContentWithFooter(lastLine, footer = footer) { text(lastLine) } }
            // Receipts share the line's row, at the far right, level with its last line.
            if (readBy.isNotEmpty()) {
                ReadReceipts(
                    readBy,
                    resolver,
                    Modifier.align(Alignment.Bottom).padding(start = GAP)
                )
            }
        }
        if (reactions.isNotEmpty()) Reactions(reactions, resolver, Modifier.padding(start = AVATAR + GAP, top = 4.dp))
    }
}

private class Avatar(
    val name: String,
    val id: String,
    val mxc: String?,
)

/** A /me message: "* Name does something", fully formatted (bold, links, emoji…), never forced italic. */
@Composable
internal fun EmoteRow(
    message: TimelineItem.Message,
    resolver: MediaResolver,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val content = message.content as MessageContent.Text
    val color = MaterialTheme.colorScheme.onSurface
    ActionLine(
        avatar = Avatar(message.label.shownName, message.label.profileId ?: message.sender, message.senderAvatarMxc),
        reactions = message.reactions,
        readBy = message.readBy,
        resolver = resolver,
        actions = actions,
        highlighted = highlighted,
        footer = { Footer(message, MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
    ) { lastLine ->
        val name = senderText(message.label)
        val prefix = remember(name) { AnnotatedString("* ") + name + AnnotatedString(" ") }
        val style = MaterialTheme.typography.bodyLarge
        val tint = if (content.kind == TextKind.Notice) color.copy(alpha = NOTICE) else color
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            message.reply?.let { reply -> Reply(reply, color, onClick = { actions.jumpTo(reply.eventId) }) }
            val html = content.html
            if (html != null) {
                HtmlContent(
                    html,
                    tint,
                    style,
                    { resolver.media(it, false) },
                    lastLine = lastLine,
                    prefix = prefix,
                    preserveWhitespace = content.plainText,
                    onOpenImage = { mxc, alt -> resolver.image(mxc, alt)?.let(actions.openMedia) },
                )
            } else {
                PlainContent(content.body, tint, style, lastLine = lastLine, prefix = prefix)
            }
        }
    }
}

/** Joins, leaves, kicks, profile changes, room settings… with the actor's name in their colour. */
@Composable
internal fun StateChangeRow(
    item: TimelineItem.StateChange,
    resolver: MediaResolver,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val time = rememberTime(item.timestamp)
    val profile = item.change as? Change.ProfileChanged
    // A profile change is told with the new name ("Alice changed their name from Ann").
    val actorName = profile?.newName ?: item.actorName
    ActionLine(
        avatar = Avatar(actorName, item.actor, item.actorAvatarMxc),
        reactions = item.reactions,
        readBy = item.readBy,
        resolver = resolver,
        actions = actions,
        highlighted = highlighted,
        footer = { Text(time, color = muted, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
    ) { lastLine ->
        val template = changeTemplate(item.change, item.actor)
        val actorColor = senderColor(item.actor)
        val text = remember(template, actorName, actorColor) { fillTemplate(template, actorName, actorColor) }
        val avatars = profile?.let { profileAvatars(it, actorName, resolver, actions) }.orEmpty()
        Text(
            text,
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
            inlineContent = avatars,
            onTextLayout = { lastLine.update(it) },
        )
    }
}

/** [ACTOR] becomes the coloured name; [OLD_AVATAR]/[NEW_AVATAR] become inline avatar images. */
private fun fillTemplate(
    template: String,
    actorName: String,
    actorColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        template.forEach { c ->
            when (c) {
                ACTOR -> withStyle(SpanStyle(color = actorColor, fontWeight = FontWeight.Medium)) { append(actorName) }
                OLD_AVATAR -> appendInlineContent(OLD_ID, "old avatar")
                NEW_AVATAR -> appendInlineContent(NEW_ID, "new avatar")
                else -> append(c)
            }
        }
    }

@Composable
private fun profileAvatars(
    change: Change.ProfileChanged,
    name: String,
    resolver: MediaResolver,
    actions: TimelineActions,
): Map<String, InlineTextContent> {
    fun inline(mxc: String?) =
        InlineTextContent(Placeholder(INLINE_AVATAR.em, INLINE_AVATAR.em, PlaceholderVerticalAlign.TextCenter)) {
            RoomAvatar(
                name,
                mxc.orEmpty(),
                resolver.avatar(mxc),
                size = INLINE_AVATAR_SIZE,
                modifier = Modifier.clip(CircleShape).clickable { resolver.image(mxc, name)?.let(actions.openMedia) },
            )
        }
    return mapOf(OLD_ID to inline(change.oldAvatar), NEW_ID to inline(change.newAvatar))
}

/** The change's sentence, with markers where the actor's name and inline avatars go. */
@Composable
private fun changeTemplate(
    change: Change,
    actor: String,
): String =
    when (change) {
        Change.Joined -> stringResource(R.string.change_joined, ACTOR)
        Change.Left -> stringResource(R.string.change_left, ACTOR)
        is Change.Invited -> stringResource(R.string.change_invited, ACTOR, change.target)
        is Change.Kicked -> stringResource(R.string.change_kicked, ACTOR, change.target)
        is Change.Banned -> stringResource(R.string.change_banned, ACTOR, change.target)
        is Change.ProfileChanged -> profileTemplate(change, actor)
        is Change.RoomName -> stringResource(R.string.change_room_name, ACTOR, change.name.orEmpty())
        is Change.RoomTopic -> stringResource(R.string.change_room_topic, ACTOR)
        Change.RoomAvatar -> stringResource(R.string.change_room_avatar, ACTOR)
        Change.RoomCreated -> stringResource(R.string.change_created, ACTOR)
        Change.EncryptionEnabled -> stringResource(R.string.change_encryption, ACTOR)
    }

@Composable
private fun profileTemplate(
    change: Change.ProfileChanged,
    actor: String,
): String {
    val oldName = change.oldName ?: actor.removePrefix("@").substringBefore(':')
    val fromTo = " " + stringResource(R.string.avatar_from_to, OLD_AVATAR.toString(), NEW_AVATAR.toString())
    val avatarPart =
        when {
            change.oldAvatar != null && change.newAvatar != null -> fromTo
            change.newAvatar != null -> " $NEW_AVATAR"
            change.oldAvatar != null -> " $OLD_AVATAR"
            else -> ""
        }
    return when {
        change.nameChanged && change.avatarChanged -> {
            stringResource(R.string.change_profile_both, ACTOR, oldName) +
                avatarPart
        }

        change.nameChanged -> {
            stringResource(R.string.change_profile_name, ACTOR, oldName)
        }

        change.newAvatar == null -> {
            stringResource(R.string.change_profile_avatar_removed, ACTOR) + avatarPart
        }

        change.oldAvatar == null -> {
            stringResource(R.string.change_profile_avatar_set, ACTOR) + avatarPart
        }

        else -> {
            stringResource(R.string.change_profile_avatar, ACTOR) + avatarPart
        }
    }
}

/** Stand in for names and images in a translated sentence, wherever the language puts them. */
private const val ACTOR = '\u0001'
private const val OLD_AVATAR = '\u0002'
private const val NEW_AVATAR = '\u0003'
private const val OLD_ID = "avatar:old"
private const val NEW_ID = "avatar:new"
private const val INLINE_AVATAR = 1.35f

/** Fills the 1.35 em placeholder at body-medium size, so the initials scale with it. */
private val INLINE_AVATAR_SIZE = 18.dp
private val AVATAR = 20.dp
private val AVATAR_NUDGE = 2.dp
private val GAP = 8.dp
private val EDGE = 12.dp
private const val NOTICE = 0.75f
