package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// What sits over a timeline picture: play, tap to show, spoiler, upload progress.

@Composable
internal fun PlayBadge() {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = SCRIM)) {
        Icon(
            painterResource(R.drawable.ic_play),
            null,
            tint = Color.White,
            modifier = Modifier.padding(12.dp).size(28.dp),
        )
    }
}

/** Over a GIF shown still: tap to play it. */
@Composable
internal fun GifBadge() {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = SCRIM)) {
        Text(
            stringResource(R.string.gif),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Over a preview that waits for a tap (gomuks' "show image and video previews" off). */
@Composable
internal fun TapToShow() {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = SCRIM)) {
        Text(
            stringResource(R.string.tap_to_show),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Over a picture its sender hid as a spoiler (MSC4193), with their reason when they gave one. */
@Composable
internal fun SpoilerBadge(reason: String?) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = SCRIM)) {
        Text(
            if (reason == null) stringResource(R.string.spoiler) else stringResource(R.string.spoiler_reason, reason),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** A ring filling up as the upload goes, on a dark disc so it reads over any image. */
@Composable
internal fun UploadProgress(progress: Float) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = SCRIM)) {
        CircularProgressIndicator(
            progress = { progress },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.padding(10.dp).size(32.dp),
        )
    }
}

private const val SCRIM = 0.45f
