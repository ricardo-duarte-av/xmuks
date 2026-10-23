package pt.aguiarvieira.xmuks.feature.room

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.media.MediaShares
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.media

/**
 * Sharing a message to another app through the system's share sheet: its file (fetched first, a
 * toast says so) with its caption, or its text. Leaving the room first calls a fetch off.
 */
@Composable
internal fun rememberMessageSharer(viewModel: MediaSaverViewModel = hiltViewModel()): (TimelineItem.Message) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, scope) {
        { message ->
            val content = message.content
            val media = content.media
            val url = media?.let { viewModel.shares.urlOf(it) }
            if (media == null || url == null) {
                (content as? MessageContent.Text)?.let { context.startActivity(MediaShares.textIntent(it.body)) }
            } else {
                val name = fileName(media)
                Toast.makeText(context, resources.getString(R.string.share_preparing, name), Toast.LENGTH_SHORT).show()
                scope.launch {
                    runCatching { viewModel.shares.intent(url, name, media.mimeType, content.caption) }
                        .onSuccess { context.startActivity(it) }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            val reason = e.message ?: e.javaClass.simpleName
                            val text = resources.getString(R.string.share_failed, name, reason)
                            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }
}

/** Whether the menu offers sharing [message]: it has a file, or text. */
internal fun shareable(message: TimelineItem.Message): Boolean =
    message.uploadProgress == null && (message.content.media != null || message.content is MessageContent.Text)

private val MessageContent.caption: String?
    get() =
        when (this) {
            is MessageContent.Image -> caption
            is MessageContent.Video -> caption
            else -> null
        }
