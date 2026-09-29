package pt.aguiarvieira.xmuks.feature.room

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A map with a pin fixed at its centre: move the map to place it. Opens on where we are (after
 * asking for location, once), which "My location" goes back to.
 */
@Composable
internal fun LocationPicker(
    onSend: (PickedLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 1f) }
    var here by remember { mutableStateOf<PickedLocation?>(null) }
    var granted by remember { mutableStateOf(hasLocation(context)) }
    val goHome = {
        scope.launch {
            val found = currentLocation(context) ?: return@launch
            here = found
            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(found.latitude, found.longitude), ZOOM))
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = result.values.any { it }
            if (granted) goHome()
        }
    LaunchedEffect(Unit) {
        if (granted) {
            goHome()
        } else {
            permission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(isMyLocationEnabled = granted),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            )
            // The pin: its point sits on the map's centre.
            Icon(
                painterResource(R.drawable.ic_location),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(bottom = PIN_SIZE).size(PIN_SIZE),
            )
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
                IconButton(
                    onClick = onDismiss
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(12.dp)
                        .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val target = camera.position.target
                    Text(
                        String.format(java.util.Locale.ROOT, "%.5f, %.5f", target.latitude, target.longitude),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (granted) {
                            FilledTonalButton(onClick = { goHome() }) { Text(stringResource(R.string.location_mine)) }
                        }
                        Button(onClick = {
                            val mine = here
                            // Still where we are (the map wasn't moved off it): send it as our position.
                            val atHome = mine != null && near(mine, target)
                            onSend(
                                PickedLocation(
                                    target.latitude,
                                    target.longitude,
                                    mine?.accuracy?.takeIf { atHome },
                                    self = atHome
                                ),
                            )
                            onDismiss()
                        }) { Text(stringResource(R.string.location_send)) }
                    }
                }
            }
        }
    }
}

private fun hasLocation(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** One fresh fix (or null: no permission, location off, no answer). */
@SuppressLint("MissingPermission") // only called once a location permission is granted
private suspend fun currentLocation(context: Context): PickedLocation? {
    if (!hasLocation(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancel = CancellationTokenSource()
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel.cancel() }
        client
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
            .addOnSuccessListener { l ->
                cont.resume(l?.let { PickedLocation(it.latitude, it.longitude, it.accuracy, self = true) })
            }.addOnFailureListener { cont.resume(null) }
    }
}

/** Within a few metres: the map is still on our position. */
private fun near(
    a: PickedLocation,
    b: LatLng,
): Boolean {
    val out = FloatArray(1)
    android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
    return out[0] < SAME_PLACE_M
}

private const val ZOOM = 16f
private const val SAME_PLACE_M = 10f
private val PIN_SIZE = 40.dp
