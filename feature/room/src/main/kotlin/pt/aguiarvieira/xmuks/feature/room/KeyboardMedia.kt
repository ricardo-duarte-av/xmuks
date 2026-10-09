@file:OptIn(ExperimentalFoundationApi::class)

package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.Reference
import java.util.UUID

/**
 * GIFs and stickers from the keyboard (Gboard's buttons hand them over as content, not text),
 * and images or videos pasted or dropped into the box. The keyboard's grant to read one doesn't
 * last, so each is copied in first, then sent as it is ([onSend]): a quick action, no preview.
 * Text is left to the field.
 */
@Composable
internal fun rememberMediaReceiver(onSend: (Uri) -> Unit): ReceiveContentListener {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val send by rememberUpdatedState(onSend)
    return remember(context, scope) {
        ReceiveContentListener { content ->
            // Decided from what the clip says it holds: asking the keyboard's provider is a call
            // into the keyboard, and it waits on our answer (giving up after a second).
            val declared = mediaTypes(content)
            if (declared.isEmpty()) return@ReceiveContentListener content
            val uris = ArrayList<Uri>()
            val rest =
                content.consume { item ->
                    item.uri?.let(uris::add) != null
                }
            scope.launch {
                val copies =
                    withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri ->
                            val type = context.contentResolver.getType(uri)?.takeIf(::isMedia) ?: declared.first()
                            copyIn(context, uri, type)
                        }
                    }
                // The keyboard's read grant is released once its content is collected: keep it
                // until the copies are made.
                Reference.reachabilityFence(content)
                copies.forEach(send)
            }
            rest
        }
    }
}

/** The picture and video types the clip says it holds. */
private fun mediaTypes(content: TransferableContent): List<String> {
    val description = content.clipEntry.clipData.description
    return (0 until description.mimeTypeCount).map(description::getMimeType).filter(::isMedia)
}

private fun isMedia(type: String) = type.startsWith("image/") || type.startsWith("video/")

/** [uri] copied into our provider's keyboard folder, named for its type; null if it can't be read. */
private fun copyIn(
    context: Context,
    uri: Uri,
    type: String,
): Uri? =
    runCatching {
        val dir = File(context.cacheDir, "keyboard").apply { mkdirs() }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: type.substringAfter('/')
        val prefix =
            when {
                type == "image/gif" -> "GIF"
                type.startsWith("video/") -> "VID"
                else -> "IMG"
            }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date())
        val file = File(dir, "${prefix}_${stamp}_${UUID.randomUUID().toString().take(4)}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { from -> file.outputStream().use { from.copyTo(it) } }
        FileProvider.getUriForFile(context, context.packageName + ".camera", file)
    }.getOrNull()
