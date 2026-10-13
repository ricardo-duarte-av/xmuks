package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import android.text.format.Formatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import pt.aguiarvieira.xmuks.core.data.timeline.Action
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.MediaIssue

/** Dims what's offered but not allowed here: it stays, and tapping it says why. */
internal fun Modifier.limited(limit: Limit?): Modifier = if (limit == null) this else alpha(DISABLED_ALPHA)

/** What tapping something [limit]ed says, about [network] (the bridge's name, when known). */
internal fun limitText(
    context: Context,
    limit: Limit,
    network: String?,
): String {
    val name = network ?: context.getString(R.string.network_fallback)
    val text =
        when (limit) {
            is Limit.Unsupported -> {
                context.getString(unsupportedText(limit.action), name)
            }

            is Limit.TooOld -> {
                val delete = limit.action == Action.Delete
                val res = if (delete) R.string.limit_delete_too_old else R.string.limit_edit_too_old
                context.getString(res, name, duration(context, limit.maxAgeSec))
            }

            Limit.ReactionNotAllowed -> {
                context.getString(R.string.limit_reaction_not_allowed, name)
            }

            is Limit.TextTooLong -> {
                context.getString(R.string.limit_text_too_long, name, limit.max)
            }

            is Limit.TooManyOptions -> {
                context.getString(R.string.limit_too_many_options, name, limit.max)
            }

            is Limit.Media -> {
                mediaText(context, limit.issue, name)
            }
        }
    return text.replaceFirstChar { it.uppercase() }
}

private fun unsupportedText(action: Action) =
    when (action) {
        Action.Edit -> R.string.limit_edit_unsupported
        Action.Delete -> R.string.limit_delete_unsupported
        Action.React -> R.string.limit_react_unsupported
        Action.Reply -> R.string.limit_reply_unsupported
        Action.Thread -> R.string.limit_thread_unsupported
        Action.Poll -> R.string.limit_poll_unsupported
        Action.Location -> R.string.limit_location_unsupported
    }

/** What a file about to be sent would come to on [network]. */
internal fun mediaText(
    context: Context,
    issue: MediaIssue,
    network: String?,
): String {
    val name = network ?: context.getString(R.string.network_fallback)
    val text =
        when (issue) {
            is MediaIssue.KindUnsupported -> {
                context.getString(R.string.limit_media_kind, name)
            }

            is MediaIssue.FormatUnsupported -> {
                context.getString(R.string.limit_media_format, name, issue.mime)
            }

            is MediaIssue.TooBig -> {
                context.getString(R.string.limit_media_too_big, name, Formatter.formatShortFileSize(context, issue.max))
            }

            MediaIssue.CaptionDropped -> {
                context.getString(R.string.limit_caption_dropped, name)
            }

            is MediaIssue.CaptionTooLong -> {
                context.getString(R.string.limit_caption_too_long, name, issue.max)
            }

            MediaIssue.Converted -> {
                context.getString(R.string.limit_converted, name)
            }
        }
    return text.replaceFirstChar { it.uppercase() }
}

/** "15 minutes", "2 days": [seconds] in the largest unit that divides them. */
internal fun duration(
    context: Context,
    seconds: Long,
): String {
    val (res, n) =
        when {
            seconds % DAY == 0L -> R.plurals.duration_days to seconds / DAY
            seconds % HOUR == 0L -> R.plurals.duration_hours to seconds / HOUR
            seconds % MINUTE == 0L -> R.plurals.duration_minutes to seconds / MINUTE
            else -> R.plurals.duration_seconds to seconds
        }
    return context.resources.getQuantityString(res, n.toInt(), n.toInt())
}

private const val MINUTE = 60L
private const val HOUR = 3600L
private const val DAY = 86_400L
