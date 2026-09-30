package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** The attachments this build can send, in the + sheet's order. */
internal val SUPPORTED_ATTACHMENTS =
    Attachment.entries

/**
 * Starts whatever gets the file for an attachment — the photo picker, the file picker, or the
 * system camera writing into a file of ours — and hands what comes back to [onPick].
 */
@Composable
internal fun rememberAttachLauncher(
    onPick: (Uri) -> Unit,
    /** Several at once: each gets its own caption, on the share screen. */
    onPickMany: (List<Uri>) -> Unit = { it.forEach(onPick) },
): (Attachment) -> Unit {
    val picked: (List<Uri>) -> Unit = { uris ->
        when (uris.size) {
            0 -> Unit
            1 -> onPick(uris.single())
            else -> onPickMany(uris)
        }
    }
    val context = LocalContext.current
    // Where the camera writes; kept across the activity being recreated while the camera is up.
    var captureTo by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKS), picked)
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), picked)
    val photo =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            captureTo?.takeIf { taken }?.let { onPick(Uri.parse(it)) }
        }
    val video =
        rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { taken ->
            captureTo?.takeIf { taken }?.let { onPick(Uri.parse(it)) }
        }
    return { attachment ->
        when (attachment) {
            Attachment.Gallery -> {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }

            Attachment.File -> {
                document.launch(arrayOf("*/*"))
            }

            Attachment.Audio -> {
                document.launch(arrayOf("audio/*"))
            }

            Attachment.Photo -> {
                val uri = captureUri(context, "jpg")
                captureTo = uri.toString()
                photo.launch(uri)
            }

            Attachment.Video -> {
                val uri = captureUri(context, "mp4")
                captureTo = uri.toString()
                video.launch(uri)
            }

            // These open their own sheets (a recorder, a map) rather than another app.
            Attachment.Voice, Attachment.Location, Attachment.Poll -> {}
        }
    }
}

/** The photo picker's cap on one pick. */
private const val MAX_PICKS = 20

/** A new file for the camera to fill, named for when it was taken, shared through our provider. */
private fun captureUri(
    context: Context,
    extension: String,
): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date())
    val prefix = if (extension == "mp4") "VID" else "IMG"
    val file = File(dir, "${prefix}_${stamp}_${UUID.randomUUID().toString().take(4)}.$extension")
    return FileProvider.getUriForFile(context, context.packageName + ".camera", file)
}
