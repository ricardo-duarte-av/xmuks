package pt.aguiarvieira.xmuks.feature.media

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/**
 * Sharing what the viewer shows to another app: the file is fetched (a toast says so), then the
 * system's share sheet. Leaving the viewer first calls it off.
 */
@Composable
internal fun rememberViewerSharer(viewModel: MediaViewerViewModel): (ViewerMedia) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, scope) {
        { media ->
            val name = suggestedName(media)
            Toast.makeText(context, resources.getString(R.string.share_preparing, name), Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching { viewModel.shares.intent(media.url, name, media.mimeType) }
                    .onSuccess { context.startActivity(it) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        val text = resources.getString(R.string.share_failed, name, e.message ?: e.javaClass.simpleName)
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
}
