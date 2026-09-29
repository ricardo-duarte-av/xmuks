package pt.aguiarvieira.xmuks.feature.room

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import java.util.Locale

/**
 * A location message: a map of the spot (Google Static Maps, when the build has a key) with its
 * description, opening in the phone's maps app when tapped.
 */
@Composable
internal fun LocationCard(
    location: MessageContent.Location,
    color: Color,
) {
    val context = LocalContext.current
    val point = remember(location.geoUri) { location.geoUri?.let(::parseGeoUri) }
    val map = remember(point) { point?.let { staticMapUrl(context, it) } }
    Column(
        modifier =
            Modifier.widthIn(max = MAP_WIDTH).clip(RoundedCornerShape(12.dp)).clickable {
                point?.let { openInMaps(context, it) }
            },
    ) {
        Box(
            Modifier.aspectRatio(2f).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (map != null) {
                AsyncImage(map, location.body, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.ic_location), null, modifier = Modifier.size(40.dp))
            }
        }
        Text(
            location.body,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/** `geo:lat,lon[,alt][;u=…]` → (lat, lon). */
internal fun parseGeoUri(uri: String): Pair<Double, Double>? {
    val coordinates =
        uri
            .removePrefix("geo:")
            .substringBefore(';')
            .substringBefore('?')
            .split(',')
    val lat = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return null
    val lon = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return null
    return lat to lon
}

private fun staticMapUrl(
    context: Context,
    point: Pair<Double, Double>,
): String? {
    val key = mapsKey(context)?.takeIf { it.isNotBlank() } ?: return null
    val (lat, lon) = point
    val at = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
    return Uri
        .parse("https://maps.googleapis.com/maps/api/staticmap")
        .buildUpon()
        .appendQueryParameter("center", at)
        .appendQueryParameter("zoom", "15")
        .appendQueryParameter("size", "400x200")
        .appendQueryParameter("scale", "2")
        .appendQueryParameter("markers", "color:red|$at")
        .appendQueryParameter("key", key)
        .build()
        .toString()
}

/** The build's Maps key, from the manifest (where the build put it). */
private fun mapsKey(context: Context): String? =
    runCatching {
        val pm = context.packageManager
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION") // the only way below API 33
                pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }
        info.metaData?.getString("com.google.android.geo.API_KEY")
    }.getOrNull()

private fun openInMaps(
    context: Context,
    point: Pair<Double, Double>,
) {
    val (lat, lon) = point
    val at = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$at?q=$at")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        // No maps app: nothing to open it in.
    }
}

private val MAP_WIDTH = 300.dp
