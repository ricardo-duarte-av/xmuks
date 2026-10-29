package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.call.system.RouteKind

/**
 * Where the call's audio goes. Two choices (earpiece and speaker) swap with a tap; with a headset or
 * a car in the mix, a menu lists them all.
 */
@Composable
internal fun RouteButton(
    ui: CallUi,
    onRoute: (AudioRoute) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconToggleButton(
            checked = ui.route?.kind == RouteKind.Speaker,
            onCheckedChange = {
                if (ui.routes.size == 2) {
                    ui.routes.firstOrNull { it != ui.route }?.let(onRoute)
                } else {
                    menu = true
                }
            },
            modifier = Modifier.size(CONTROL_SIZE),
        ) {
            Icon(
                painterResource(routeIcon(ui.route?.kind)),
                contentDescription = stringResource(R.string.call_audio_route)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ui.routes.forEach { route ->
                DropdownMenuItem(
                    text = { Text(route.name) },
                    leadingIcon = { Icon(painterResource(routeIcon(route.kind)), null) },
                    onClick = {
                        menu = false
                        onRoute(route)
                    },
                )
            }
        }
    }
}

internal fun routeIcon(kind: RouteKind?) =
    when (kind) {
        RouteKind.Speaker -> R.drawable.ic_speaker
        RouteKind.Bluetooth -> R.drawable.ic_bluetooth
        RouteKind.WiredHeadset -> R.drawable.ic_headset
        else -> R.drawable.ic_call
    }

/** Where the call's audio starts: the system's pick, or any device it offers. */
@Composable
internal fun StartingRouteButton(
    routes: List<AudioRoute>,
    chosen: AudioRoute?,
    onRoute: (AudioRoute?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconToggleButton(
            checked = chosen != null,
            onCheckedChange = { menu = true },
            modifier = Modifier.size(LOBBY_CONTROL),
        ) {
            Icon(
                painterResource(routeIcon(chosen?.kind)),
                contentDescription = stringResource(R.string.call_audio_route)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.call_lobby_route_automatic)) },
                leadingIcon = { Icon(painterResource(routeIcon(null)), null) },
                onClick = {
                    menu = false
                    onRoute(null)
                },
            )
            routes.forEach { route ->
                DropdownMenuItem(
                    text = { Text(route.name) },
                    leadingIcon = { Icon(painterResource(routeIcon(route.kind)), null) },
                    onClick = {
                        menu = false
                        onRoute(route)
                    },
                )
            }
        }
    }
}
