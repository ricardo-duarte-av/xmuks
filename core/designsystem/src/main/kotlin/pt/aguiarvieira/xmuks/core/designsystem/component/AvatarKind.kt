package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** What an avatar represents; each kind has its own silhouette so they read apart at a glance. */
enum class AvatarKind {
    /** People and DMs: circles. */
    Person,

    /** Rooms: rounded squares. */
    Room,

    /** Spaces: the expressive 9-sided cookie, so a space is never mistaken for a room. */
    Space,
}

/** Clip shape for [this] kind; also the shape real (Coil-loaded) avatars are clipped to. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarKind.shape(): Shape =
    when (this) {
        AvatarKind.Person -> CircleShape
        AvatarKind.Room -> RoomAvatarShape
        AvatarKind.Space -> MaterialShapes.Cookie9Sided.toShape()
    }

private val RoomAvatarShape = RoundedCornerShape(percent = 30)
