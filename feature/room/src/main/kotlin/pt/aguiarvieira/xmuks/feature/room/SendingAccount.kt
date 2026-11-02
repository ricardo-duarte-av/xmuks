package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.AvatarShape
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import pt.aguiarvieira.xmuks.core.designsystem.component.SharedKeys
import pt.aguiarvieira.xmuks.core.designsystem.component.sharedElement

/** The account a room is written in as: who, as its avatar shows them. */
@Immutable
data class SendingAccount(
    /** The account's local ID: what the room list's avatar chip is keyed by. */
    val id: String,
    val name: String,
    val userId: String,
    val avatarUrl: String?,
)

/**
 * The account shown, small on the room's avatar (top right), in a ring of the surface colour so it
 * reads over the avatar. It flies in from the room list's avatar chip, which it is.
 */
@Composable
internal fun AccountBadge(
    account: SendingAccount,
    sharedScope: String,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.sending_as, account.userId)
    Box(
        modifier
            .sharedElement(SharedKeys.account(account.id, sharedScope))
            .background(MaterialTheme.colorScheme.surface, AvatarShape)
            .padding(RING)
            .semantics { contentDescription = label },
    ) {
        RoomAvatar(
            name = account.name,
            id = account.userId,
            avatarUrl = account.avatarUrl,
            size = BADGE_SIZE - RING * 2
        )
    }
}

/** [AccountBadge] for the room header's avatar; none without an [account] to show. */
internal fun accountBadge(
    account: SendingAccount?,
    sharedScope: String,
): (@Composable () -> Unit)? = account?.let { { AccountBadge(it, sharedScope) } }

private val BADGE_SIZE = 18.dp
private val RING = 1.5.dp
