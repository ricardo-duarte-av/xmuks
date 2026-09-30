package pt.aguiarvieira.xmuks.feature.profile

import android.net.Uri
import androidx.compose.runtime.Immutable
import pt.aguiarvieira.xmuks.core.data.push.RoomNotifications
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.designsystem.component.ViewerMedia

/** Everything the room info screen can change, and where it can lead. */
@Immutable
class RoomInfoActions(
    val setName: (String) -> Unit = {},
    val setTopic: (String) -> Unit = {},
    val setAvatar: (Uri?) -> Unit = {},
    val setJoinRule: (String) -> Unit = {},
    val setHistoryVisibility: (String) -> Unit = {},
    val enableEncryption: () -> Unit = {},
    val setNotifications: (RoomNotifications) -> Unit = {},
    val setLevel: (userId: String, level: Long) -> Unit = { _, _ -> },
    val membership: (userId: String, MembershipAction, reason: String?) -> Unit = { _, _, _ -> },
    val leave: (reason: String?) -> Unit = {},
    val personas: PersonaEdits = PersonaEdits({ _, _ -> }, {}, { _, _ -> }),
    val openUser: (String) -> Unit = {},
    val openMembers: () -> Unit = {},
    val openPreferences: () -> Unit = {},
    val openState: () -> Unit = {},
    val openMedia: (ViewerMedia) -> Unit = {},
)
