package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.ScreenCard

/** Avatar, name, address (tap to copy), topic, and whether it's encrypted and how many are in it. */
@Composable
internal fun RoomHeroCard(
    info: RoomInfo,
    me: String,
    media: ProfileMedia,
    actions: RoomInfoActions,
    modifier: Modifier = Modifier,
) {
    val levels = info.powerLevels
    val name = info.name ?: info.canonicalAlias ?: info.roomId
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    @Suppress("DEPRECATION") // The suspend Clipboard API needs ClipEntry plumbing for plain text.
    val clipboard = LocalClipboardManager.current
    ScreenCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ImageSlot(
                mxc = info.avatarMxc,
                title = stringResource(R.string.room_avatar),
                viewer = { media.viewer(info.avatarMxc, name) },
                onOpenMedia = actions.openMedia,
                onChange = actions.setAvatar.takeIf { levels.canSetState(me, "m.room.avatar") },
                modifier = Modifier.clip(CircleShape),
            ) {
                RoomAvatar(name, info.roomId, media.thumbnail(info.avatarMxc), size = AVATAR_SIZE)
            }
            EditableLine(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                onEdit = { editing = NAME }.takeIf { levels.canSetState(me, "m.room.name") },
            )
            val address = info.canonicalAlias ?: info.roomId
            Text(
                address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(8.dp)
                        ).clickable { clipboard.setText(AnnotatedString(address)) },
            )
            Text(
                listOf(
                    stringResource(if (info.encrypted) R.string.room_encrypted else R.string.room_not_encrypted),
                    stringResource(R.string.room_members_count, info.members(Membership.Join).size),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditableLine(
                text = info.topic ?: stringResource(R.string.room_topic_empty),
                style = MaterialTheme.typography.bodyMedium,
                onEdit = { editing = TOPIC }.takeIf { levels.canSetState(me, "m.room.topic") },
                dim = info.topic == null,
            )
            if (info.replacementRoom != null) {
                Text(
                    stringResource(R.string.room_upgraded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    when (editing) {
        NAME -> {
            TextDialog(
                stringResource(R.string.room_name),
                info.name.orEmpty(),
                true,
                actions.setName,
                { editing = null }
            )
        }

        TOPIC -> {
            TextDialog(stringResource(R.string.room_topic), info.topic.orEmpty(), false, actions.setTopic, {
                editing =
                    null
            })
        }
    }
}

@Composable
private fun EditableLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    onEdit: (() -> Unit)?,
    dim: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = style,
            textAlign = TextAlign.Center,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onEdit != null) {
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit)) }
        }
    }
}

private enum class Setting { Notifications, JoinRule, History, Encryption }

/** Notifications, who can join, who can read history, encryption: each changeable with the power to. */
@Composable
internal fun RoomSettingsCard(
    info: RoomInfo,
    me: String,
    notifications: RoomNotifications,
    actions: RoomInfoActions,
    modifier: Modifier = Modifier,
) {
    val levels = info.powerLevels
    var open by rememberSaveable { mutableStateOf<Setting?>(null) }
    ScreenCard(modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow(
                R.drawable.ic_notifications,
                R.string.room_notifications,
                notificationLabel(notifications),
                onClick = { open = Setting.Notifications },
            )
            SettingRow(
                R.drawable.ic_person_add,
                R.string.room_access,
                JOIN_RULES[info.joinRule] ?: R.string.join_private,
                onClick = { open = Setting.JoinRule }.takeIf { levels.canSetState(me, "m.room.join_rules") },
            )
            SettingRow(
                R.drawable.ic_history,
                R.string.room_history,
                HISTORY[info.historyVisibility] ?: R.string.history_shared,
                onClick = { open = Setting.History }.takeIf { levels.canSetState(me, "m.room.history_visibility") },
            )
            val canEncrypt = !info.encrypted && levels.canSetState(me, "m.room.encryption")
            SettingRow(
                R.drawable.ic_lock,
                when {
                    info.encrypted -> R.string.room_encrypted
                    canEncrypt -> R.string.room_encryption_enable
                    else -> R.string.room_not_encrypted
                },
                null,
                onClick = { open = Setting.Encryption }.takeIf { canEncrypt },
            )
            Text(
                stringResource(R.string.room_version, info.roomVersion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
    val close = { open = null }
    when (open ?: return) {
        Setting.Notifications -> {
            RoomNotificationsDialog(notifications, actions.setNotifications, close)
        }

        Setting.JoinRule -> {
            ChoiceDialog(
                R.string.room_access,
                JOIN_RULE_CHOICES,
                info.joinRule,
                actions.setJoinRule,
                close
            )
        }

        Setting.History -> {
            ChoiceDialog(
                R.string.room_history,
                HISTORY_CHOICES,
                info.historyVisibility,
                actions.setHistoryVisibility,
                close
            )
        }

        Setting.Encryption -> {
            ConfirmDialog(
                R.string.room_encryption_enable,
                R.string.room_encryption_confirm,
                actions.enableEncryption,
                close
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: Int,
    title: Int,
    value: Int?,
    onClick: (() -> Unit)?,
) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), null) },
        supportingContent = value?.let { { Text(stringResource(it)) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) { Text(stringResource(title)) }
}

/** Leaving, after asking (with a warning when getting back in needs an invite). */
@Composable
internal fun LeaveCard(
    info: RoomInfo,
    onLeave: (reason: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    ScreenCard(modifier) {
        ListItem(
            leadingContent = {
                Icon(
                    painterResource(R.drawable.ic_leave),
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { asking = true },
        ) { Text(stringResource(R.string.leave_room), color = MaterialTheme.colorScheme.error) }
    }
    if (asking) {
        val name = info.name ?: info.canonicalAlias ?: info.roomId
        ReasonDialog(
            title = stringResource(R.string.leave_confirm, name),
            message = stringResource(R.string.leave_private_warning).takeIf { info.joinRule != "public" },
            onConfirm = onLeave,
            onDismiss = { asking = false },
        )
    }
}

private fun notificationLabel(setting: RoomNotifications) =
    when (setting) {
        RoomNotifications.Default -> R.string.notify_default
        RoomNotifications.All -> R.string.notify_all
        RoomNotifications.MentionsAndKeywords -> R.string.notify_mentions
        RoomNotifications.Off -> R.string.notify_off
    }

private const val NAME = "name"
private const val TOPIC = "topic"
private val AVATAR_SIZE = 96.dp

internal val JOIN_RULES =
    mapOf(
        "public" to R.string.join_public,
        "invite" to R.string.join_invite,
        "knock" to R.string.join_knock,
        "restricted" to R.string.join_restricted,
        "knock_restricted" to R.string.join_knock_restricted,
    )

/** Restricted rules need the spaces chosen too: offered only when already set (shown, not picked). */
private val JOIN_RULE_CHOICES = JOIN_RULES.filterKeys { it in setOf("public", "invite", "knock") }

private val HISTORY =
    mapOf(
        "world_readable" to R.string.history_world_readable,
        "shared" to R.string.history_shared,
        "invited" to R.string.history_invited,
        "joined" to R.string.history_joined,
    )
private val HISTORY_CHOICES = HISTORY
