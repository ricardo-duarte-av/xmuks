package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.NetworkBadge
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement

/** In a bridged room's header: the other network's logo, flown in from the room list's badge. */
@Composable
internal fun BridgeBadge(
    bridge: BridgeInfo,
    resolver: MediaResolver,
    roomId: String,
    sharedScope: String,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.bridged_to, bridge.protocol)
    NetworkBadge(
        bridge.protocol,
        resolver.avatar(bridge.protocolAvatarMxc),
        size = 32.dp,
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .sharedElement(SharedKeys.bridge(roomId, sharedScope))
                .semantics { contentDescription = label },
    )
}
