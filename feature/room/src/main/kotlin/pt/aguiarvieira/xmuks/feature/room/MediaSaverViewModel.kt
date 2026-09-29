package pt.aguiarvieira.xmuks.feature.room

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import pt.aguiarvieira.xmuks.core.data.media.DownloadResult
import pt.aguiarvieira.xmuks.core.data.media.MediaDownloads
import pt.aguiarvieira.xmuks.core.data.timeline.Media
import javax.inject.Inject

@HiltViewModel
class MediaSaverViewModel
    @Inject
    constructor(
        val downloads: MediaDownloads,
    ) : ViewModel()

/**
 * Saving media: the system's "save as" (so the user picks where, and no storage permission is
 * needed), then the download, told with a toast when it's done or failed.
 */
@Composable
internal fun rememberMediaSaver(viewModel: MediaSaverViewModel = hiltViewModel()): (Media) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    // What's being saved, while the "save as" screen is up (kept across its round trip).
    var pending by rememberSaveable { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val (mxc, encrypted, name) = pending ?: return@rememberLauncherForActivityResult
            pending = null
            if (uri != null) {
                viewModel.downloads.save(mxc, encrypted, name, uri)
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
            val name = fileName(media)
            pending = Triple(media.mxc, media.encrypted, name)
            create.launch(name)
        }
    }
}

/** The name to suggest: the file's own, with an extension for its type when it has none. */
internal fun fileName(media: Media): String {
    val base = media.name?.takeIf { it.isNotBlank() && !it.contains('\n') }?.replace('/', '_') ?: "file"
    if (base.substringAfterLast('.', "").isNotEmpty()) return base
    val extension = media.mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return if (extension == null) base else "$base.$extension"
}
