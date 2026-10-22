package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import pt.aguiarvieira.xmuks.core.data.media.PdfFiles
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent

/**
 * A PDF: the top of its first page (with previews on, and not too big a file to fetch for it)
 * above its name and size. A tap opens it.
 */
@Composable
internal fun PdfCard(
    file: MessageContent.File,
    color: Color,
    resolver: MediaResolver,
    onOpen: () -> Unit,
) {
    val display = LocalMediaDisplay.current
    val small = (file.media.size ?: 0) <= PdfFiles.PREVIEW_BYTES
    val url = resolver.file(file.media)?.takeIf { display.showPreviews && small && it.startsWith("http") }
    // Gone if the page can't be drawn: the card is still there.
    var failed by remember(url) { mutableStateOf(false) }
    Column(Modifier.tapOrHold(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (url != null && !failed) {
            val preview = PdfFiles.preview(url)
            val modifier =
                Modifier
                    .widthIn(max = display.maxWidth)
                    .aspectRatio(PREVIEW_RATIO)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            val onState = { state: AsyncImagePainter.State ->
                if (state is AsyncImagePainter.State.Error) failed = true
            }
            val loader = resolver.images
            if (loader != null) {
                AsyncImage(
                    preview,
                    file.name,
                    loader,
                    modifier,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    onState = onState,
                )
            } else {
                AsyncImage(
                    preview,
                    file.name,
                    modifier,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    onState = onState,
                )
            }
        }
        FileCard(R.drawable.ic_file, file.name, file.media.size, color)
    }
}

/** The page's top, wider than tall: enough to tell what it is. */
private const val PREVIEW_RATIO = 4f / 3f
