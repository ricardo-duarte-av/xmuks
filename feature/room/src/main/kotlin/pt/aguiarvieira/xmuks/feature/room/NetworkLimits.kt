package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.FileFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.Support

/**
 * What the network a room is bridged to supports, as its bridge reports it: message actions,
 * reactions, formatting, each kind of file, and the rest. From the room header's menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkLimitsSheet(
    features: RoomFeatures,
    network: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val name = network ?: stringResource(R.string.network_fallback)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.network_limits), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.network_limits_intro, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            MessagesSection(context, features)
            ReactionsSection(features)
            if (features.formatting.isNotEmpty()) FormattingSection(features.formatting)
            if (features.files.isNotEmpty()) MediaSection(context, features.files)
            OtherSection(context, features)
        }
    }
}

@Composable
private fun MessagesSection(
    context: Context,
    features: RoomFeatures,
) {
    Section(R.string.limits_messages) {
        Line(
            stringResource(R.string.limits_text_length),
            features.maxTextLength?.let { stringResource(R.string.limits_up_to_chars, it) }
                ?: stringResource(R.string.limits_no_limit),
        )
        Timed(context, R.string.limits_edit, features.edit, features.editMaxAgeSec)
        Timed(context, R.string.limits_delete, features.delete, features.deleteMaxAgeSec)
        Level(stringResource(R.string.limits_replies), features.reply)
        Level(stringResource(R.string.limits_threads), features.thread)
        val options = features.pollMaxOptions
        val length = features.pollOptionMaxLength
        Level(
            stringResource(R.string.limits_polls),
            features.poll,
            detail =
                if (features.poll.works && options != null && length != null) {
                    stringResource(R.string.limits_poll_details, options, length)
                } else {
                    null
                },
        )
        Level(stringResource(R.string.limits_location), features.locationMessage)
    }
}

@Composable
private fun ReactionsSection(features: RoomFeatures) {
    Section(R.string.limits_reactions) {
        Level(stringResource(R.string.limits_reactions), features.reaction)
        if (features.reaction.works) {
            Line(
                stringResource(R.string.limits_per_person),
                features.reactionCount?.toString() ?: stringResource(R.string.limits_no_limit),
            )
            Line(
                stringResource(R.string.limits_allowed_reactions),
                features.allowedReactions?.joinToString(" ") ?: stringResource(R.string.limits_any),
            )
            YesNo(stringResource(R.string.limits_custom_emoji), features.customEmojiReactions)
        }
    }
}

@Composable
private fun FormattingSection(formatting: Map<String, Support>) {
    Section(R.string.limits_formatting) {
        // Known ones in their order, then any others.
        formatting.entries.sortedBy { FORMATTING_ORDER.indexOf(it.key).toUInt() }.forEach {
            Level(FORMATTING[it.key]?.let { res -> stringResource(res) } ?: it.key, it.value)
        }
    }
}

@Composable
private fun MediaSection(
    context: Context,
    files: Map<String, FileFeatures>,
) {
    Section(R.string.limits_media) {
        val known = MEDIA.keys.filter { it in files }
        (known + (files.keys - MEDIA.keys).sorted()).forEach { msgtype ->
            val file = files.getValue(msgtype)
            Column(Modifier.padding(vertical = 6.dp)) {
                Line(
                    MEDIA[msgtype]?.let { stringResource(it) } ?: msgtype,
                    file.maxSize?.let {
                        stringResource(R.string.limits_max_size, Formatter.formatShortFileSize(context, it))
                    } ?: stringResource(R.string.limits_no_limit),
                )
                Level(stringResource(R.string.limits_caption), file.caption, indent = true)
                Formats(file)
            }
        }
    }
}

/** A kind of file's formats, grouped: accepted as they are, converted, refused. */
@Composable
private fun Formats(file: FileFeatures) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    val groups =
        listOf(
            Triple(R.string.limits_formats_full, file.mimeTypes.filterValues { it == Support.Full }.keys, muted),
            Triple(R.string.limits_formats_partial, file.mimeTypes.filterValues { it == Support.Partial }.keys, muted),
            Triple(R.string.limits_formats_none, file.mimeTypes.filterValues { !it.works }.keys, error),
        )
    Column(Modifier.padding(start = INDENT)) {
        groups.filter { it.second.isNotEmpty() }.forEach { (label, mimes, color) ->
            Text(
                stringResource(label, mimes.sorted().joinToString()),
                color = color,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OtherSection(
    context: Context,
    features: RoomFeatures,
) {
    Section(R.string.limits_other) {
        Line(
            stringResource(R.string.limits_disappearing),
            features.disappearingTimers.takeIf { it.isNotEmpty() }?.joinToString { duration(context, it / MILLIS) }
                ?: stringResource(R.string.support_unsupported),
        )
        YesNo(stringResource(R.string.limits_typing), features.typingNotifications)
        YesNo(stringResource(R.string.limits_receipts), features.readReceipts)
    }
}

/** A titled group of lines. */
@Composable
private fun Section(
    title: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        HorizontalDivider()
        content()
    }
}

/** An action that's possible only so long after sending. */
@Composable
private fun Timed(
    context: Context,
    label: Int,
    support: Support,
    maxAgeSec: Long?,
) = Level(
    stringResource(label),
    support,
    detail = maxAgeSec?.takeIf { support.works }?.let { stringResource(R.string.limits_within, duration(context, it)) },
)

/** [label], and how well the network does it ([detail] under, when there's more to say). */
@Composable
private fun Level(
    label: String,
    support: Support,
    detail: String? = null,
    indent: Boolean = false,
) {
    val (text, color) =
        when (support) {
            Support.Full -> stringResource(R.string.support_full) to MaterialTheme.colorScheme.primary
            Support.Partial -> stringResource(R.string.support_partial) to MaterialTheme.colorScheme.tertiary
            Support.Unsupported -> stringResource(R.string.support_unsupported) to MaterialTheme.colorScheme.error
            Support.Dropped -> stringResource(R.string.support_dropped) to MaterialTheme.colorScheme.error
            Support.Rejected -> stringResource(R.string.support_rejected) to MaterialTheme.colorScheme.error
        }
    Line(label, "${mark(support)} $text", color, detail, indent)
}

@Composable
private fun YesNo(
    label: String,
    yes: Boolean,
) = Line(
    label,
    stringResource(if (yes) R.string.limits_yes else R.string.limits_no),
    if (yes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Line(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    detail: String? = null,
    indent: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) INDENT else 0.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.End)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun mark(support: Support) =
    when (support) {
        Support.Full -> "✓"
        Support.Partial -> "~"
        else -> "✗"
    }

/** Each kind of file, in the order shown. */
private val MEDIA =
    linkedMapOf(
        RoomFeatures.IMAGE to R.string.media_image,
        RoomFeatures.VIDEO to R.string.media_video,
        RoomFeatures.AUDIO to R.string.media_audio,
        RoomFeatures.VOICE to R.string.media_voice,
        RoomFeatures.FILE to R.string.media_file,
        RoomFeatures.STICKER to R.string.media_sticker,
        RoomFeatures.GIF to R.string.media_gif,
    )

/** mautrix's formatting features, in the order shown. */
private val FORMATTING =
    linkedMapOf(
        "bold" to R.string.format_bold,
        "italic" to R.string.format_italic,
        "strikethrough" to R.string.format_strikethrough,
        "underline" to R.string.format_underline,
        "inline_code" to R.string.format_inline_code,
        "code_block" to R.string.format_code_block,
        "blockquote" to R.string.format_blockquote,
        "headers" to R.string.format_headers,
        "inline_link" to R.string.format_inline_link,
        "user_link" to R.string.format_user_link,
        "room_link" to R.string.format_room_link,
        "unordered_list" to R.string.format_unordered_list,
        "ordered_list" to R.string.format_ordered_list,
        "ordered_list.start" to R.string.format_ordered_list_start,
        "spoiler" to R.string.format_spoiler,
        "table" to R.string.format_table,
        "horizontal_line" to R.string.format_horizontal_line,
        "text_foreground_color" to R.string.format_text_color,
        "text_background_color" to R.string.format_text_background,
        "math" to R.string.format_math,
    )

private val FORMATTING_ORDER = FORMATTING.keys.toList()

private val INDENT = 16.dp
private const val MILLIS = 1000L
