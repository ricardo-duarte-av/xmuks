package pt.aguiarvieira.xmuks.feature.room

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.NetworkBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement

/**
 * In a bridged room's header: the other network's logo, flown in from the room list's badge. A tap
 * shows the network's limits (or says there are none to show).
 */
@Composable
internal fun BridgeBadge(
    bridge: BridgeInfo,
    resolver: MediaResolver,
    roomId: String,
    sharedScope: String,
    /** Null when the bridge doesn't report its network's limits: a tap says so. */
    onNetworkLimits: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.bridged_to, bridge.protocol)
    val context = LocalContext.current
    val unavailable = stringResource(R.string.network_limits_unavailable, bridge.protocol)
    val onClick = { onNetworkLimits?.invoke() ?: Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
    NetworkBadge(
        bridge.protocol,
        resolver.avatar(bridge.protocolAvatarMxc),
        size = 32.dp,
        ringed = false,
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .sharedElement(SharedKeys.bridge(roomId, sharedScope))
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
    )
}
