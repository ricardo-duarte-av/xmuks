package pt.aguiarvieira.xmuks.feature.media

import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import pt.aguiarvieira.xmuks.core.data.media.DownloadResult
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/**
 * Saving what the viewer shows: the system's "save as" (the user picks where, no storage
 * permission), then the download, told with a toast when it's done or failed.
 */
@Composable
internal fun rememberViewerSaver(viewModel: MediaViewerViewModel): (ViewerMedia) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    // What's being saved, while the "save as" screen is up (kept across its round trip).
    var pending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val (url, name) = pending ?: return@rememberLauncherForActivityResult
            pending = null
            if (uri != null) {
                viewModel.downloads.save(url, name, uri)
                Toast.makeText(context, resources.getString(R.string.saving, name), Toast.LENGTH_SHORT).show()
            }
        }
    LaunchedEffect(viewModel) {
        viewModel.downloads.results.collect { result ->
            val text =
                when (result) {
                    is DownloadResult.Saved -> resources.getString(R.string.saved, result.name)
                    is DownloadResult.Failed -> resources.getString(R.string.save_failed, result.name, result.reason)
                }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    return remember(create) {
        { media ->
            val name = suggestedName(media)
            pending = media.url to name
            create.launch(name)
        }
    }
}

/** The file's own name, with an extension for its type when it has none; else one by its kind. */
internal fun suggestedName(media: ViewerMedia): String {
    val base =
        media.name?.takeIf { it.isNotBlank() && !it.contains('\n') }?.replace('/', '_')
            ?: when (media.kind) {
                ViewerMedia.Kind.Image -> "image"
                ViewerMedia.Kind.Video -> "video"
                ViewerMedia.Kind.Audio -> "audio"
                ViewerMedia.Kind.Pdf -> "document"
                ViewerMedia.Kind.File -> "file"
            }
    if (base.substringAfterLast('.', "").isNotEmpty()) return base
    val extension = media.mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return if (extension == null) base else "$base.$extension"
}
