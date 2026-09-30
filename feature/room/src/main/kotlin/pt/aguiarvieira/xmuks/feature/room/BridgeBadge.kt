package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.BridgeInfo
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/** In a bridged room's header: the other network's logo. */
@Composable
internal fun BridgeBadge(
    bridge: BridgeInfo,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.bridged_to, bridge.protocol)
    RoomAvatar(
        bridge.protocol,
        bridge.protocol,
        resolver.avatar(bridge.protocolAvatarMxc),
        modifier = modifier.padding(horizontal = 4.dp).semantics { contentDescription = label },
        size = 28.dp,
    )
}
