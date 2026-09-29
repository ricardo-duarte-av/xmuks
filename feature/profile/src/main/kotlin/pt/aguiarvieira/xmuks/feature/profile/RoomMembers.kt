package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomMember
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCards

/** The sections shown, in order: people knocking first (they're waiting on someone). */
private val SECTIONS =
    listOf(
        Membership.Knock to R.string.members_knocking,
        Membership.Join to R.string.members,
        Membership.Invite to R.string.members_invited,
        Membership.Ban to R.string.members_banned,
    )

/**
 * The member list as one long card, one lazy row per member: the screen scrolls through all of
 * them, and only those on screen are ever composed, so rooms with thousands stay smooth. A header
 * with search and invite, then each section. Tapping someone opens what can be done about them.
 */
internal fun LazyListScope.memberItems(
    info: RoomInfo,
    me: String,
    search: TextFieldState,
    media: ProfileMedia,
    actions: RoomInfoActions,
) {
    val query =
        search.text
            .toString()
            .trim()
            .lowercase()
    val matching =
        info.members.filter {
            query.isEmpty() || query in it.name.lowercase() || query in it.userId.lowercase()
        }
    item(key = "members-header") {
        MembersHeader(info, me, search, actions, Modifier.cardPart(top = true, bottom = false))
    }
    SECTIONS.forEach { (membership, title) ->
        val section = matching.filter { it.membership == membership }
        if (section.isEmpty()) return@forEach
        item(key = "section-$membership") {
            Text(
                "${stringResource(title)} · ${section.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.cardPart(top = false, bottom = false).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        items(section, key = { "m-${it.userId}" }, contentType = { "member" }) { member ->
            MemberRow(member, info, me, media, actions, Modifier.cardPart(top = false, bottom = false))
        }
    }
    item(key = "members-end") {
        Row(Modifier.cardPart(top = false, bottom = true).padding(bottom = 12.dp)) {}
    }
}

/** One slice of a card: rounded where the card starts or ends, on the card's colour. */
@Composable
private fun Modifier.cardPart(
    top: Boolean,
    bottom: Boolean,
): Modifier {
    val radius = ScreenCards.Radius
    val shape =
        RoundedCornerShape(
            topStart = if (top) radius else 0.dp,
            topEnd = if (top) radius else 0.dp,
            bottomStart = if (bottom) radius else 0.dp,
            bottomEnd = if (bottom) radius else 0.dp,
        )
    return fillMaxWidth()
        .then(if (bottom) Modifier.padding(bottom = ScreenCards.Gap) else Modifier)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
}

@Composable
private fun MembersHeader(
    info: RoomInfo,
    me: String,
    search: TextFieldState,
    actions: RoomInfoActions,
    modifier: Modifier = Modifier,
) {
    var inviting by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.members),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (info.powerLevels.canInvite(me)) {
                IconButton(onClick = { inviting = true }) {
                    Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.invite))
                }
            }
        }
        OutlinedTextField(
            state = search,
            placeholder = { Text(stringResource(R.string.members_search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp),
        )
    }
    if (inviting) {
        InviteDialog(
            onInvite = { actions.membership(it, MembershipAction.Invite, null) },
            onDismiss = { inviting = false },
        )
    }
}

@Composable
private fun MemberRow(
    member: RoomMember,
    info: RoomInfo,
    me: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val usersDefault = info.powerLevels.usersDefault
    ListItem(
        leadingContent = { RoomAvatar(member.name, member.userId, media.thumbnail(member.avatarMxc), size = 40.dp) },
        supportingContent = {
            Text(
                member.reason ?: member.userId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent =
            if (member.powerLevel != usersDefault) {
                { Text(roleName(member.powerLevel, usersDefault), style = MaterialTheme.typography.labelMedium) }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable { open = true },
    ) { Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (open) MemberSheet(member, info, me, media, actions) { open = false }
}
