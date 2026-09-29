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
    listOf(Attachment.Gallery, Attachment.Photo, Attachment.Video, Attachment.File, Attachment.Audio)

/**
 * Starts whatever gets the file for an attachment — the photo picker, the file picker, or the
 * system camera writing into a file of ours — and hands what comes back to [onPick].
 */
@Composable
internal fun rememberAttachLauncher(onPick: (Uri) -> Unit): (Attachment) -> Unit {
    val context = LocalContext.current
    // Where the camera writes; kept across the activity being recreated while the camera is up.
    var captureTo by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPick) }
    val document =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPick) }
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

            // Not offered yet (see SUPPORTED_ATTACHMENTS).
            Attachment.Voice, Attachment.Location -> {}
        }
    }
}

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
