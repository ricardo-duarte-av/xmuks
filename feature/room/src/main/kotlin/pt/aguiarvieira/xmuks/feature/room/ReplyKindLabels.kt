package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyKind

/** What a replied-to event without text was, said by its sender (whose name is just above). */
@Composable
internal fun replyKindLabel(kind: ReplyKind): String =
    when (kind) {
        is ReplyKind.Reaction -> {
            kind.key?.let { stringResource(R.string.reply_reaction, it) }
                ?: stringResource(R.string.reply_reaction_unknown)
        }

        is ReplyKind.Changed -> {
            changeLabel(kind.change)
        }

        ReplyKind.PinsChanged -> {
            stringResource(R.string.reply_pins)
        }

        ReplyKind.PermissionsChanged -> {
            stringResource(R.string.reply_permissions)
        }

        ReplyKind.Deleted -> {
            stringResource(R.string.reply_deleted)
        }

        ReplyKind.Undecryptable -> {
            stringResource(R.string.reply_undecryptable)
        }

        is ReplyKind.Other -> {
            stringResource(R.string.reply_other, kind.type)
        }
    }

@Composable
private fun changeLabel(change: Change): String =
    when (change) {
        Change.Joined -> {
            stringResource(R.string.reply_joined)
        }

        Change.Left -> {
            stringResource(R.string.reply_left)
        }

        is Change.Invited -> {
            stringResource(R.string.reply_invited, change.target)
        }

        is Change.Kicked -> {
            stringResource(R.string.reply_kicked, change.target)
        }

        is Change.Banned -> {
            stringResource(R.string.reply_banned, change.target)
        }

        is Change.ProfileChanged -> {
            stringResource(profileLabel(change))
        }

        is Change.RoomName -> {
            change.name?.let { stringResource(R.string.reply_room_name, it) }
                ?: stringResource(R.string.reply_room_name_removed)
        }

        is Change.RoomTopic -> {
            stringResource(R.string.reply_topic)
        }

        Change.RoomAvatar -> {
            stringResource(R.string.reply_room_avatar)
        }

        Change.RoomCreated -> {
            stringResource(R.string.reply_created)
        }

        Change.EncryptionEnabled -> {
            stringResource(R.string.reply_encryption)
        }
    }

private fun profileLabel(change: Change.ProfileChanged) =
    when {
        change.nameChanged && change.avatarChanged -> R.string.reply_name_avatar
        change.nameChanged -> R.string.reply_name
        change.avatarChanged -> R.string.reply_avatar
        else -> R.string.reply_profile
    }
